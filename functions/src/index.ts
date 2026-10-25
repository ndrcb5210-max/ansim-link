import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { logger } from 'firebase-functions';

import { Collections, LocationFields, MemberFields, MessageKeys } from './schema';
import {
  type AlertSnapshot,
  type MemberSnapshot,
  type PushPayload,
  chunkTokens,
  isShareJustStarted,
  recipientTokens,
  safetyMessagePayload,
  shareStartedPayload,
  shouldClearSharing,
  shouldDeleteLocation,
} from './messages';

/**
 * 안심연결 Cloud Functions (2세대).
 *
 * **배포하지 않는다.** Cloud Functions 는 Blaze 요금제가 필요하므로 코드만 두고
 * 배포 절차는 functions/README.md 에 적어 두었다.
 *
 * ## 왜 서버가 필요한가
 * FCM 발송에는 서버 자격 증명이 필요하다. 그 키를 앱에 넣으면 APK 를 뜯어 꺼낸 누구나
 * 아무에게나 푸시를 보낼 수 있다. 그래서 앱은 Firestore 문서만 만들고
 * (`groups/{gid}/alerts/{id}`) 서버가 그것을 보고 보낸다.
 *
 * ## 판단과 배선을 나눠 두었다
 * '누구에게 보낼지 / 무엇을 보낼지 / 무엇을 지울지'는 messages.ts 의 순수 함수에 있고
 * 단위 테스트가 달려 있다. 이 파일은 Firestore 를 읽어 그 함수에 넘기고 결과를
 * FCM·Firestore 에 쓰는 배선뿐이다.
 *
 * ## 지역
 * 모든 함수를 `asia-northeast3`(서울)에 둔다. Firestore 도 같은 지역에 두면
 * 지연이 줄고 리전 간 트래픽 비용이 없다.
 */

initializeApp();

const firestore = getFirestore();

/** 모든 함수가 쓰는 공통 설정. */
const REGION = 'asia-northeast3';

/**
 * 안심 메시지가 만들어지면 그룹 구성원에게 푸시를 보낸다(보낸 사람 제외).
 *
 * 실패해도 재시도하지 않는다(`retry` 기본값 false). 재난 상황의 메시지는 늦게 두 번
 * 가는 것보다 한 번에 가거나 마는 쪽이 낫고, 앱 쪽에 이미 WorkManager 재시도가 있다.
 */
export const onAlertCreated = onDocumentCreated(
  {
    document: 'groups/{groupId}/alerts/{alertId}',
    region: REGION,
  },
  async (event) => {
    const { groupId, alertId } = event.params;
    const alert = event.data?.data() as AlertSnapshot | undefined;
    if (!alert) {
      logger.warn('안심 메시지 문서가 비어 있다', { groupId, alertId });
      return;
    }

    const senderUid = alert.senderUid;
    if (!senderUid) {
      logger.warn('보낸 사람이 없어 보내지 않는다', { groupId, alertId });
      return;
    }

    const members = await readMembers(groupId);
    const tokens = recipientTokens(members, senderUid);
    if (tokens.length === 0) {
      logger.info('보낼 토큰이 없다', { groupId, alertId });
      return;
    }

    const payload = safetyMessagePayload(alert, groupId, alertId, tokens);
    await sendToTokens(payload, { groupId, alertId });
  },
);

/**
 * 구성원이 공유를 시작하면 다른 구성원에게 알린다.
 *
 * 구성원 문서는 이름·일시중지·푸시 토큰 때문에도 바뀌므로, `sharing.active` 가
 * false(또는 없음) → true 로 바뀐 순간만 잡는다([isShareJustStarted]).
 *
 * `onAlertCreated` 와 겹치지 않나: 겹칠 수 있다. 사용자가 '공유 시작'을 누르면 둘 다
 * 간다. 그래도 둘 다 두는 이유는 **공유 시작 경로가 메시지 발송과 독립**이기 때문이다.
 * 앱에서 '지금 공유'를 누르면 재난문자와 무관하게 공유만 시작하고 메시지는 가지 않는다.
 * 그때도 가족은 '누가 공유를 시작했다'를 알아야 한다. 받는 쪽 알림은 세션 ID 로
 * 묶이므로(앱의 알림 ID 계산) 같은 세션에서 두 줄로 쌓이지는 않는다.
 */
export const onShareStarted = onDocumentWritten(
  {
    document: 'groups/{groupId}/members/{memberId}',
    region: REGION,
  },
  async (event) => {
    const { groupId, memberId } = event.params;

    const before = event.data?.before?.data()?.[MemberFields.sharing];
    const after = event.data?.after?.data()?.[MemberFields.sharing];
    if (!isShareJustStarted(before, after)) return;

    const afterDocument = event.data?.after?.data();
    const senderName = (afterDocument?.[MemberFields.displayName] as string | undefined) ?? '';
    const sessionId = (after?.sessionId as string | undefined) ?? '';

    const members = await readMembers(groupId);
    const tokens = recipientTokens(members, memberId);
    if (tokens.length === 0) {
      logger.info('보낼 토큰이 없다', { groupId, memberId });
      return;
    }

    const payload = shareStartedPayload(senderName, groupId, sessionId, tokens);
    await sendToTokens(payload, { groupId, memberId });
  },
);

/**
 * 만료된 위치와 멈춰 있는 공유 표시를 정리한다.
 *
 * CLAUDE.md 원칙 4: 세션이 끝나면 구성원별 마지막 위치 1건만 남기고, 그것도 24시간 뒤
 * 지운다. 앱이 `expiresAt` 에 '세션 종료 + 24시간'을 적어 두므로 서버는 그 값만 본다.
 *
 * ## 왜 한 시간마다인가
 * '24시간 뒤'를 분 단위로 맞출 이유가 없고, 자주 돌면 그만큼 읽기 비용이 든다.
 * 한 시간마다면 최악의 경우 25시간까지 남는데, 그 사실을 개인정보 처리방침에 적을 수 있다.
 *
 * ## 컬렉션 그룹 질의를 쓴다
 * 그룹을 하나씩 훑으면 그룹 수만큼 질의가 필요하다. `collectionGroup('locations')` 에
 * `expiresAt <= now` 를 걸면 지울 것만 한 번에 가져온다. 그러려면 Firestore 색인이
 * 필요한데, 한 필드 색인은 자동으로 만들어진다.
 */
export const cleanupExpiredLocations = onSchedule(
  {
    // 매시 7분에 돈다. 정각을 피한 이유는 다른 예약 작업과 겹치지 않게 하려는 것이다.
    schedule: '7 * * * *',
    timeZone: 'Asia/Seoul',
    region: REGION,
  },
  async () => {
    const nowMillis = Date.now();

    const expired = await firestore
      .collectionGroup(Collections.locations)
      .where(LocationFields.expiresAt, '<=', nowMillis)
      .limit(BATCH_LIMIT)
      .get();

    let deleted = 0;
    if (!expired.empty) {
      const batch = firestore.batch();
      for (const document of expired.docs) {
        // 순수 함수로 한 번 더 확인한다. 질의에 걸리지 않는 모양(expiresAt 없음)도 있다.
        if (shouldDeleteLocation(document.data(), nowMillis)) {
          batch.delete(document.ref);
          deleted += 1;
        }
      }
      if (deleted > 0) await batch.commit();
    }

    const cleared = await clearStaleSharing(nowMillis);

    logger.info('정리 끝', { deleted, cleared, nowMillis });
  },
);

/**
 * `expiresAt` 이 아예 없는 위치 문서를 정리한다.
 *
 * 위 질의(`expiresAt <= now`)는 그 필드가 없는 문서를 아예 가져오지 못한다
 * (Firestore 는 필드가 없는 문서를 범위 질의에서 뺀다). 예전 버전이 쓴 문서나
 * 어긋난 문서가 영원히 남을 수 있으므로 하루 한 번 따로 훑는다.
 *
 * 하루 한 번인 이유: 보통은 한 건도 없다. 매시 훑으면 읽기 비용만 든다.
 */
export const cleanupOrphanLocations = onSchedule(
  {
    schedule: '23 4 * * *',
    timeZone: 'Asia/Seoul',
    region: REGION,
  },
  async () => {
    const nowMillis = Date.now();
    const all = await firestore
      .collectionGroup(Collections.locations)
      .limit(BATCH_LIMIT)
      .get();

    let deleted = 0;
    const batch = firestore.batch();
    for (const document of all.docs) {
      if (shouldDeleteLocation(document.data(), nowMillis)) {
        batch.delete(document.ref);
        deleted += 1;
      }
    }
    if (deleted > 0) await batch.commit();

    logger.info('남은 위치 문서 정리', { scanned: all.size, deleted });
  },
);

// ---- 내부 도우미 ----

/** 한 번에 처리할 문서 수. 함수 하나가 너무 오래 돌지 않게 끊는다. */
const BATCH_LIMIT = 400;

async function readMembers(groupId: string): Promise<MemberSnapshot[]> {
  const snapshot = await firestore
    .collection(Collections.groups)
    .doc(groupId)
    .collection(Collections.members)
    .get();

  return snapshot.docs.map((document) => {
    const data = document.data();
    return {
      uid: document.id,
      displayName: data[MemberFields.displayName] as string | undefined,
      pushToken: data[MemberFields.pushToken] as string | undefined,
      consent: data[MemberFields.consent] as { location?: boolean } | undefined,
      paused: data[MemberFields.paused] as boolean | undefined,
    };
  });
}

/**
 * 데이터 메시지를 보낸다.
 *
 * `priority: 'high'` 를 쓰는 이유: 데이터 메시지는 기본 우선순위에서 Doze 중에 늦게
 * 올 수 있다. 재난 상황의 메시지는 바로 가야 한다.
 *
 * 쓸 수 없게 된 토큰은 지운다. 앱을 지웠거나 데이터를 비운 기기의 토큰이 쌓이면
 * 보낼 때마다 실패하고 비용만 든다.
 */
async function sendToTokens(
  payload: PushPayload,
  context: Record<string, string>,
): Promise<void> {
  const messaging = getMessaging();
  const invalidTokens: string[] = [];

  for (const chunk of chunkTokens(payload.tokens)) {
    const response = await messaging.sendEachForMulticast({
      tokens: chunk,
      data: payload.data,
      android: {
        priority: 'high',
      },
    });

    response.responses.forEach((result, index) => {
      if (result.success) return;
      const code = result.error?.code;
      logger.warn('푸시 실패', { ...context, code });
      if (
        code === 'messaging/registration-token-not-registered' ||
        code === 'messaging/invalid-registration-token' ||
        code === 'messaging/invalid-argument'
      ) {
        const token = chunk[index];
        if (token) invalidTokens.push(token);
      }
    });

    logger.info('푸시 보냄', {
      ...context,
      type: payload.data[MessageKeys.type],
      success: response.successCount,
      failure: response.failureCount,
    });
  }

  if (invalidTokens.length > 0) await removeInvalidTokens(invalidTokens);
}

/**
 * 쓸 수 없는 토큰을 지운다.
 *
 * 원본(`users/{uid}/pushTokens/{token}`)과 사본(`members/{uid}.pushToken`) 두 군데에
 * 있다. 원본은 문서 ID 가 토큰이라 바로 찾을 수 있지만 uid 를 모른다. 사본은
 * 컬렉션 그룹 질의로 찾는다.
 *
 * 실패해도 넘어간다. 다음 발송에서 다시 걸린다.
 */
async function removeInvalidTokens(tokens: string[]): Promise<void> {
  for (const token of tokens) {
    try {
      const copies = await firestore
        .collectionGroup(Collections.members)
        .where(MemberFields.pushToken, '==', token)
        .get();

      const batch = firestore.batch();
      for (const document of copies.docs) {
        batch.update(document.ref, { [MemberFields.pushToken]: null });
        // 사본의 부모 경로에서 uid 를 얻어 원본도 지운다.
        batch.delete(
          firestore
            .collection(Collections.users)
            .doc(document.id)
            .collection(Collections.pushTokens)
            .doc(token),
        );
      }
      if (!copies.empty) await batch.commit();
    } catch (error) {
      logger.warn('쓸 수 없는 토큰을 지우지 못했다', { error: String(error) });
    }
  }
}

/** 멈춰 있는 `sharing.active` 를 끈다. */
async function clearStaleSharing(nowMillis: number): Promise<number> {
  const sharing = await firestore
    .collectionGroup(Collections.members)
    .where(`${MemberFields.sharing}.active`, '==', true)
    .limit(BATCH_LIMIT)
    .get();

  if (sharing.empty) return 0;

  let cleared = 0;
  const batch = firestore.batch();
  for (const document of sharing.docs) {
    const value = document.data()[MemberFields.sharing];
    if (shouldClearSharing(value, nowMillis)) {
      batch.update(document.ref, { [`${MemberFields.sharing}.active`]: false });
      cleared += 1;
    }
  }
  if (cleared > 0) await batch.commit();
  return cleared;
}

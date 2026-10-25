import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { onMessagePublished } from 'firebase-functions/v2/pubsub';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { defineSecret, defineString } from 'firebase-functions/params';
import { logger } from 'firebase-functions';
import { createHash } from 'node:crypto';
import { google } from 'googleapis';

import {
  Collections,
  EntitlementFields,
  LocationFields,
  MemberFields,
  MessageKeys,
  SubscriptionProducts,
  UserFields,
} from './schema';
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
import {
  type EntitlementDocument,
  type SubscriptionPurchaseV2,
  accountMatches,
  decodeNotification,
  notificationAction,
  revokedEntitlementDocument,
  toEntitlementDocument,
} from './entitlement';

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

/** 모든 함수가 쓰는 공통 설정. 앱의 `AppContainer.FUNCTIONS_REGION` 과 같아야 한다. */
const REGION = 'asia-northeast3';

/**
 * 구독 검증에 쓰는 설정. **값은 저장소에 두지 않는다**(CLAUDE.md 원칙 6).
 *
 * `ANDROID_PACKAGE_NAME` 은 비밀이 아니지만 프로젝트마다 다르므로 설정으로 받는다.
 * 비어 있으면 알림의 패키지 검사를 건너뛴다.
 */
const androidPackageName = defineString('ANDROID_PACKAGE_NAME', { default: '' });

/**
 * Play Developer API 를 부를 서비스 계정 키(JSON 전문).
 *
 * **되도록 비워 두는 쪽이 낫다.** 비우면 함수의 기본 서비스 계정(ADC)으로 부른다.
 * 그러려면 Play Console > 사용자 및 권한에서 그 서비스 계정 이메일을 초대하고
 * '재무 데이터 보기' 권한을 주면 된다. 키 파일이 아예 없으니 유출될 것도 없다.
 *
 * 그 방법이 막혔을 때(Play Console 과 Cloud 프로젝트를 연결할 수 없는 경우)만
 * 이 비밀값에 키 JSON 을 넣는다. 절차는 functions/README.md 에 적어 두었다.
 */
const playServiceAccountJson = defineSecret('PLAY_SERVICE_ACCOUNT_JSON');

/**
 * 실시간 개발자 알림(RTDN)을 받는 Pub/Sub 주제.
 *
 * Play Console > 수익 창출 설정 > 실시간 개발자 알림에 적는 주제와 같아야 한다.
 * 함수 선언에 쓰는 값이라 상수여야 하고(설정으로 받을 수 없다) 비밀도 아니다.
 */
const PLAY_NOTIFICATION_TOPIC = 'play-billing-notifications';

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

/**
 * 구매 토큰을 받아 Play 에 확인하고 `users/{uid}.entitlement` 를 쓴다.
 *
 * **앱은 이 함수의 응답으로 프리미엄을 열지 않는다.** 응답은 화면에 안내를 고르는 데만
 * 쓰고, 기능을 여는 근거는 이 함수가 쓴 Firestore 문서 하나다. 응답을 믿고 열면
 * 중간에서 응답을 바꿔치기하는 것으로 프리미엄이 열린다.
 *
 * ## 왜 서버만 할 수 있나
 * Play Developer API 는 서비스 계정 자격 증명이 필요하다. 그 키를 앱에 넣으면 APK 를
 * 뜯어 꺼낸 누구나 남의 구독을 조회할 수 있다. 4단계에서 FCM 발송을 서버에 둔 것과
 * 같은 이유다.
 *
 * ## 확인(acknowledge)은 앱이 한다
 * 여기서 `purchases.subscriptions.acknowledge` 를 부르지 않는다. 앱이 이 함수의 성공을
 * 받은 뒤에 Billing 라이브러리로 확인한다. 서버에서 확인하면 '검증은 됐는데 앱은 그
 * 사실을 모르는' 상태가 생기고, 앱이 또 확인을 시도해 혼선이 생긴다. 어느 쪽이든
 * 3일 안에 하면 되므로 한쪽에서만 한다.
 */
export const verifySubscription = onCall(
  {
    region: REGION,
    secrets: [playServiceAccountJson],
    // 구독 검증은 사용자가 결제 직후 기다리는 작업이다. 길게 두지 않는다.
    timeoutSeconds: 60,
    // 최소 인스턴스를 두지 않는다(비용). 콜드 스타트가 섞이면 느릴 수 있어서
    // 앱은 15초까지 기다리고 그 뒤에는 '다시 확인' 버튼을 준다.
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError('unauthenticated', '로그인이 필요합니다.');
    }

    const purchaseToken = asNonEmptyString(request.data?.purchaseToken);
    if (!purchaseToken) {
      throw new HttpsError('invalid-argument', '구매 토큰이 없습니다.');
    }
    const productId =
      asNonEmptyString(request.data?.productId) ?? SubscriptionProducts.premiumMonthly;

    const packageName = androidPackageName.value();
    if (!packageName) {
      // 설정하지 않으면 어느 앱의 구매인지 지정할 수 없다. 이 값이 없으면 API 를
      // 부를 수도 없으므로 '다시 시도할 수 있는' 오류로 보낸다.
      logger.error('ANDROID_PACKAGE_NAME 설정이 없다');
      throw new HttpsError('internal', '서버 설정이 끝나지 않았습니다.');
    }

    let purchase: SubscriptionPurchaseV2;
    try {
      purchase = await readSubscription(packageName, purchaseToken);
    } catch (error) {
      const status = (error as { code?: number }).code;
      // 404 = 없는 토큰, 410 = 사라진 구매. 둘 다 다시 시도해도 달라지지 않는다.
      if (status === 404 || status === 410) {
        logger.warn('Play 가 이 구매를 모른다', { uid, status });
        throw new HttpsError('not-found', '구매를 확인할 수 없습니다.');
      }
      logger.error('Play Developer API 호출이 실패했다', { uid, error: String(error) });
      throw new HttpsError('unavailable', '잠시 뒤 다시 시도해 주세요.');
    }

    // 이 구매가 이 계정의 것인지 확인한다. 앱이 결제할 때 넘긴 obfuscatedAccountId 는
    // uid 의 SHA-256 이다(SubscriptionVerifier.kt 의 obfuscatedAccountId).
    if (!accountMatches(purchase, obfuscatedAccountId(uid))) {
      logger.warn('다른 계정의 구매 토큰이다', { uid });
      throw new HttpsError('permission-denied', '다른 계정의 구독입니다.');
    }

    const document = toEntitlementDocument(purchase, productId, Date.now());
    await writeEntitlement(uid, document);
    // RTDN 에는 uid 가 없다. 이 매핑이 없으면 갱신·해지 알림을 받아도 누구의 구독인지
    // 알 수 없다. 실패해도 검증 자체는 성공으로 둔다 — 다음 검증에서 다시 남는다.
    await rememberPurchaseToken(uid, purchaseToken).catch((error: unknown) => {
      logger.warn('구매 토큰 매핑을 남기지 못했다', { uid, error: String(error) });
    });

    logger.info('구독을 확인했다', {
      uid,
      state: document.state,
      plan: document.plan,
      test: purchase.testPurchase !== undefined,
    });

    // 앱이 쓰는 것은 state 뿐이다(안내 문구를 고른다). 기간·금액은 돌려주지 않는다 —
    // 앱은 Firestore 에서 읽는다.
    return { state: document.state };
  },
);

/**
 * 실시간 개발자 알림(RTDN)을 받는다. **골격만 두었고 배포하지 않았다.**
 *
 * ## 이 함수가 필요한 이유
 * `verifySubscription` 은 사용자가 앱을 열 때만 돈다. 갱신·해지·환불·보류는 앱을
 * 열지 않아도 일어난다. 그때 Firestore 를 고치지 않으면
 * - 해지한 사람이 기간이 끝난 뒤에도 프리미엄으로 보이고(앱의 기간 검사가 막지만
 *   `state` 는 영원히 `active` 로 남는다),
 * - 환불받은 사람의 프리미엄이 그대로 남는다.
 *
 * ## 알림 종류만 보고 고치지 않는다
 * 알림은 순서가 보장되지 않는다. 늦게 온 '갱신됨'으로 이미 해지된 구독을 되살리면
 * 안 된다. 그래서 거의 모든 알림을 '**Play 에 지금 상태를 다시 물어본다**'로 처리한다
 * (`notificationAction`). 회수·환불만 예외로 즉시 막는다 — 다시 물어보는 동안 열려
 * 있는 틈을 두지 않는다.
 *
 * ## uid 를 어떻게 찾나
 * 알림에는 구매 토큰만 있고 uid 가 없다. 그래서 `verifySubscription` 이 검증할 때
 * `purchaseTokens/{tokenHash}` 에 uid 를 남겨 둔다(토큰 자체를 문서 ID 로 쓰지 않는다 —
 * 문서 ID 는 로그·색인에 남는데 구매 토큰은 비밀값이다).
 *
 * ## 설정
 * Play Console > 수익 창출 설정 > 실시간 개발자 알림에서 Pub/Sub 주제를 지정한다.
 * 주제 이름은 아래 [TOPIC] 과 같아야 한다.
 */
export const onPlayNotification = onMessagePublished(
  {
    topic: PLAY_NOTIFICATION_TOPIC,
    region: REGION,
    secrets: [playServiceAccountJson],
    // 재시도하지 않는다. 같은 알림이 다시 와도 하는 일은 '지금 상태를 다시 물어보기'라
    // 멱등이고, 실패한 알림을 끝없이 다시 받으면 Developer API 쿼터만 쓴다.
    // 놓친 상태는 사용자가 앱을 열 때 verifySubscription 이 바로잡는다.
    retry: false,
  },
  async (event) => {
    const notification = decodeNotification(event.data.message.data);
    const action = notificationAction(notification, androidPackageName.value());

    if (action.kind === 'ignore') {
      logger.info('알림을 처리하지 않는다', { reason: action.reason });
      return;
    }

    const uid = await findUidByPurchaseToken(action.purchaseToken);
    if (!uid) {
      // 아직 검증한 적이 없는 토큰이다. 사용자가 앱을 열면 verifySubscription 이
      // 돌면서 매핑을 남기고 상태도 맞춰진다.
      logger.info('이 구매 토큰의 사용자를 모른다');
      return;
    }

    if (action.kind === 'revoke') {
      const productId = await readEntitlementProductId(uid);
      await writeEntitlement(uid, revokedEntitlementDocument(productId, Date.now()));
      logger.info('구독을 회수했다', { uid });
      return;
    }

    const packageName = androidPackageName.value();
    if (!packageName) {
      logger.error('ANDROID_PACKAGE_NAME 설정이 없어 다시 확인할 수 없다');
      return;
    }

    try {
      const purchase = await readSubscription(packageName, action.purchaseToken);
      const document = toEntitlementDocument(
        purchase,
        SubscriptionProducts.premiumMonthly,
        Date.now(),
      );
      await writeEntitlement(uid, document);
      logger.info('알림을 받아 구독 상태를 갱신했다', { uid, state: document.state });
    } catch (error) {
      logger.error('알림 처리 중 Play 조회가 실패했다', { uid, error: String(error) });
    }
  },
);

// ---- 내부 도우미 ----

/** 한 번에 처리할 문서 수. 함수 하나가 너무 오래 돌지 않게 끊는다. */
const BATCH_LIMIT = 400;

/** 빈 문자열과 문자열이 아닌 값을 걸러 낸다. callable 함수의 입력은 아무것이나 올 수 있다. */
function asNonEmptyString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/**
 * uid 를 Play 에 넘길 식별값으로 바꾼다.
 *
 * 앱의 `obfuscatedAccountId(uid)` 와 **같은 계산**이어야 한다(SHA-256 16진수).
 * 한쪽만 고치면 모든 검증이 'permission-denied' 로 막힌다.
 */
function obfuscatedAccountId(uid: string): string {
  return createHash('sha256').update(uid, 'utf8').digest('hex');
}

/**
 * 구매 토큰을 문서 ID 로 쓰지 않기 위한 해시.
 *
 * 구매 토큰은 비밀값인데 문서 ID 는 색인·로그·오류 메시지에 그대로 남는다.
 * 해시를 ID 로 쓰면 '토큰 → uid' 조회는 되면서 토큰 자체가 남지 않는다.
 */
function purchaseTokenHash(purchaseToken: string): string {
  return createHash('sha256').update(purchaseToken, 'utf8').digest('hex');
}

/**
 * Play Developer API 클라이언트를 만든다.
 *
 * 자격 증명은 두 길 중 하나다.
 * 1. `PLAY_SERVICE_ACCOUNT_JSON` 비밀값이 있으면 그 키로.
 * 2. 없으면 함수의 기본 서비스 계정(ADC)으로. **이쪽을 권한다** — 키 파일이 아예
 *    없으니 유출될 것도 없다. Play Console 에서 그 계정을 초대해 두면 된다.
 */
function playApiClient() {
  const rawKey = playServiceAccountJson.value();
  const scopes = ['https://www.googleapis.com/auth/androidpublisher'];

  const auth = rawKey
    ? new google.auth.GoogleAuth({
        credentials: JSON.parse(rawKey) as Record<string, unknown>,
        scopes,
      })
    : new google.auth.GoogleAuth({ scopes });

  return google.androidpublisher({ version: 'v3', auth });
}

/** `purchases.subscriptionsv2.get` 을 부른다. */
async function readSubscription(
  packageName: string,
  purchaseToken: string,
): Promise<SubscriptionPurchaseV2> {
  const client = playApiClient();
  const response = await client.purchases.subscriptionsv2.get({ packageName, token: purchaseToken });
  return (response.data ?? {}) as SubscriptionPurchaseV2;
}

/**
 * `users/{uid}.entitlement` 를 쓴다.
 *
 * `merge: true` 로 그 필드만 바꾼다. 문서 전체를 덮어쓰면 이름·동의 기록이 사라진다.
 *
 * 같은 쓰기에서 구매 토큰 → uid 매핑도 남긴다. RTDN 에는 uid 가 없어서 이 매핑이
 * 없으면 알림을 받아도 누구의 구독인지 알 수 없다.
 */
async function writeEntitlement(uid: string, document: EntitlementDocument): Promise<void> {
  await firestore
    .collection(Collections.users)
    .doc(uid)
    .set({ [UserFields.entitlement]: document }, { merge: true });
}

/** 구매 토큰 → uid 매핑을 남긴다. 토큰은 해시해서 문서 ID 로 쓴다. */
async function rememberPurchaseToken(uid: string, purchaseToken: string): Promise<void> {
  await firestore
    .collection(Collections.purchaseTokens)
    .doc(purchaseTokenHash(purchaseToken))
    .set({ uid, updatedAt: Date.now() });
}

/** 구매 토큰으로 uid 를 찾는다. 모르면 null. */
async function findUidByPurchaseToken(purchaseToken: string): Promise<string | null> {
  const snapshot = await firestore
    .collection(Collections.purchaseTokens)
    .doc(purchaseTokenHash(purchaseToken))
    .get();
  if (!snapshot.exists) return null;
  const uid = snapshot.data()?.uid;
  return typeof uid === 'string' && uid.length > 0 ? uid : null;
}

/** 지금 적혀 있는 상품 ID. 회수 문서를 쓸 때 그대로 남기려고 읽는다. */
async function readEntitlementProductId(uid: string): Promise<string | null> {
  const snapshot = await firestore.collection(Collections.users).doc(uid).get();
  const entitlement = snapshot.data()?.[UserFields.entitlement] as
    | Record<string, unknown>
    | undefined;
  const productId = entitlement?.[EntitlementFields.productId];
  return typeof productId === 'string' ? productId : null;
}

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

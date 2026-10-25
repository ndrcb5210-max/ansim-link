import { MessageKeys, MessageTypes } from './schema';

/**
 * 순수 함수 묶음. Firebase 를 전혀 모른다.
 *
 * 왜 떼어 두는가: Cloud Functions 를 단위 테스트하려면 보통 firebase-functions-test 와
 * 에뮬레이터가 필요한데, 그러면 테스트가 느리고 CI 에서 깨지기 쉽다. 판단이 들어가는
 * 부분(누구에게 보낼지, 무엇을 보낼지, 무엇을 지울지)을 여기로 빼면 그 부분만
 * `node --test` 로 바로 확인할 수 있다. 남는 쪽(index.ts)은 Firestore 를 읽어
 * 이 함수들에 넘기고 결과를 FCM·Firestore 에 쓰는 배선뿐이다.
 */

// 아래 두 인터페이스의 선택 필드에 `| undefined` 를 직접 적었다.
// tsconfig 의 exactOptionalPropertyTypes 가 켜져 있어서 `field?: string` 만으로는
// 명시적인 undefined 를 담을 수 없는데, Firestore 문서에서 읽은 값은 필드가 없을 때
// undefined 가 된다. 그 값을 그대로 넣을 수 있어야 한다.

/** `groups/{gid}/members/{uid}` 에서 서버가 보는 부분. */
export interface MemberSnapshot {
  uid: string;
  displayName?: string | undefined;
  pushToken?: string | null | undefined;
  consent?: { location?: boolean | undefined } | null | undefined;
  paused?: boolean | undefined;
}

/** `groups/{gid}/alerts/{id}` 한 건. */
export interface AlertSnapshot {
  senderUid?: string | undefined;
  senderName?: string | undefined;
  message?: string | undefined;
  sharing?: boolean | undefined;
}

/** FCM 에 넘길 데이터 메시지 한 건. */
export interface PushPayload {
  tokens: string[];
  data: Record<string, string>;
}

/** 알림 내용이 비었을 때 쓰는 기본 문구. 앱의 SafetyMessages.DEFAULT 와 같다. */
export const DEFAULT_MESSAGE = '재난문자 받았어. 나는 괜찮아. 앱에서 내 위치를 확인해 줘.';

/** FCM 한 번에 보낼 수 있는 토큰 수(sendEachForMulticast 상한). */
export const MAX_TOKENS_PER_BATCH = 500;

/**
 * 안심 메시지를 받을 토큰을 고른다.
 *
 * 규칙
 * 1. **보낸 사람은 뺀다.** 자기 휴대폰에 자기 메시지가 오면 안 된다.
 * 2. 토큰이 없는 사람은 뺀다(앱을 아직 안 열었거나 알림 권한을 거절했다).
 * 3. **동의를 철회한 사람은 뺀다.** 메시지 자체에는 위치가 없지만, 동의를 철회해
 *    그룹에서 빠지는 중인 사람에게 그룹 소식을 계속 보내는 것은 맞지 않다.
 * 4. **일시중지한 사람은 뺀다.** '이 그룹에 내 소식을 보내지 않겠다'는 뜻으로 눌렀을
 *    가능성이 높고, 앱도 같은 판단을 한다(SafetyMessageWorker).
 * 5. 같은 토큰이 두 번 들어오면 한 번만 보낸다(기기 하나에 중복 등록된 경우).
 */
export function recipientTokens(members: MemberSnapshot[], senderUid: string): string[] {
  const tokens = members
    .filter((member) => member.uid !== senderUid)
    .filter((member) => member.consent?.location === true)
    .filter((member) => member.paused !== true)
    .map((member) => member.pushToken)
    .filter((token): token is string => typeof token === 'string' && token.length > 0);

  return [...new Set(tokens)];
}

/** 토큰을 [MAX_TOKENS_PER_BATCH] 묶음으로 나눈다. 무료 그룹은 한 묶음을 넘지 않는다. */
export function chunkTokens(
  tokens: string[],
  size: number = MAX_TOKENS_PER_BATCH,
): string[][] {
  if (size <= 0) throw new Error('묶음 크기는 1 이상이어야 한다.');
  const chunks: string[][] = [];
  for (let index = 0; index < tokens.length; index += size) {
    chunks.push(tokens.slice(index, index + size));
  }
  return chunks;
}

/**
 * 안심 메시지 푸시를 만든다.
 *
 * **데이터 메시지만 보낸다**(`notification` 블록을 쓰지 않는다). 그래야 앱이 알림 모양을
 * 정할 수 있다. `notification` 을 쓰면 앱이 백그라운드일 때 시스템이 알림을 직접 띄워
 * 버려서 [위치 보기] 버튼을 붙일 수 없고 알림 ID 를 우리가 정할 수 없다.
 *
 * 값은 모두 문자열이어야 한다(FCM 데이터 메시지 제약). boolean 은 'true'/'false' 로 넣는다.
 */
export function safetyMessagePayload(
  alert: AlertSnapshot,
  groupId: string,
  alertId: string,
  tokens: string[],
): PushPayload {
  const message = (alert.message ?? '').trim();
  return {
    tokens,
    data: {
      [MessageKeys.type]: MessageTypes.safetyMessage,
      [MessageKeys.groupId]: groupId,
      [MessageKeys.alertId]: alertId,
      [MessageKeys.senderName]: (alert.senderName ?? '').trim(),
      [MessageKeys.message]: message.length > 0 ? message : DEFAULT_MESSAGE,
      [MessageKeys.sharing]: alert.sharing === true ? 'true' : 'false',
    },
  };
}

/** 다른 구성원이 공유를 시작했다는 푸시. */
export function shareStartedPayload(
  senderName: string,
  groupId: string,
  sessionId: string,
  tokens: string[],
): PushPayload {
  return {
    tokens,
    data: {
      [MessageKeys.type]: MessageTypes.shareStarted,
      [MessageKeys.groupId]: groupId,
      // 앱은 이 값으로 알림 ID 를 나눈다. 세션 ID 를 쓰면 같은 세션의 알림이 겹쳐 쓰인다.
      [MessageKeys.alertId]: sessionId,
      [MessageKeys.senderName]: senderName.trim(),
    },
  };
}

/**
 * `members/{uid}.sharing` 이 '방금 공유를 시작한 것'으로 바뀌었는지.
 *
 * 왜 이 판단이 필요한가: `onShareStarted` 는 구성원 문서가 바뀔 때마다 깨어난다.
 * 그 문서는 이름·일시중지·푸시 토큰 때문에도 바뀌므로, 공유 시작이 아닌 변경으로
 * 알림을 보내면 안 된다. `active` 가 **false(또는 없음) → true** 로 바뀐 순간만 잡는다.
 *
 * `sessionId` 까지 보는 이유: 한 세션 안에서 endsAt 이 바뀔 수 있는데(공유 시간 연장)
 * 그때 또 알림을 보내면 안 된다.
 */
export function isShareJustStarted(
  before: { active?: boolean; sessionId?: string } | null | undefined,
  after: { active?: boolean; sessionId?: string } | null | undefined,
): boolean {
  if (after?.active !== true) return false;
  if (before?.active !== true) return true;
  // 둘 다 active 면, 세션이 바뀐 경우만 새 시작으로 본다.
  return before.sessionId !== after.sessionId;
}

/**
 * 지울 위치 문서를 고른다.
 *
 * CLAUDE.md 원칙 4: 세션이 끝나면 구성원별 마지막 위치 1건만 남기고, 그것도 24시간 뒤
 * 지운다. 앱이 `expiresAt` 에 '세션 종료 + 24시간'을 적어 두므로 서버는 그 값만 본다.
 *
 * `expiresAt` 이 없는 문서도 지운다. 예전 버전이 쓴 문서이거나 어긋난 문서인데,
 * 언제 지워야 할지 알 수 없는 위치 문서를 영원히 두는 것보다 지우는 쪽이 맞다.
 */
export function shouldDeleteLocation(
  document: { expiresAt?: unknown },
  nowMillis: number,
): boolean {
  const expiresAt = document.expiresAt;
  if (typeof expiresAt !== 'number' || !Number.isFinite(expiresAt)) return true;
  return expiresAt <= nowMillis;
}

/**
 * 공유 표시를 꺼야 하는지.
 *
 * 앱이 강제 종료되면 `sharing.active` 를 false 로 되돌리지 못해 영원히 '공유 중'으로
 * 남는다. 앱 쪽도 예정 종료 시각을 같이 보지만(GroupMember.sharingNow), 서버가 값을
 * 정리해 두면 다른 기기에서도 깔끔하게 보인다.
 *
 * 여유를 두는 이유: 기기 시계와 서버 시계가 조금 다를 수 있다. 아직 공유 중인 사람의
 * 표시를 서버가 꺼 버리면 가족 위치 보기에서 사라져 버린다.
 */
export const SHARING_GRACE_MILLIS = 5 * 60 * 1000;

export function shouldClearSharing(
  sharing: { active?: boolean; endsAt?: unknown } | null | undefined,
  nowMillis: number,
): boolean {
  if (sharing?.active !== true) return false;
  const endsAt = sharing.endsAt;
  // 끝 시각을 모르면서 '공유 중'인 문서는 어긋난 상태다. 꺼 둔다.
  if (typeof endsAt !== 'number' || !Number.isFinite(endsAt)) return true;
  return endsAt + SHARING_GRACE_MILLIS <= nowMillis;
}

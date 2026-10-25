// Firestore 컬렉션·필드 이름.
//
// 같은 이름을 앱(app/.../data/FirestoreSchema.kt)과 규칙(firestore.rules)도 쓴다.
// 셋을 같이 고쳐야 한다.
//
// 구조는 FirestoreSchema.kt 주석에 그려 두었다. 여기서는 서버가 읽고 쓰는 것만 적는다.

export const Collections = {
  users: 'users',
  pushTokens: 'pushTokens',
  groups: 'groups',
  members: 'members',
  locations: 'locations',
  alerts: 'alerts',
  provisionLogs: 'provisionLogs',

  /**
   * (5단계) `purchaseTokens/{tokenHash}` — 구매 토큰 → uid 매핑. **서버만 쓰고 읽는다.**
   *
   * 실시간 개발자 알림(RTDN)에는 구매 토큰만 있고 uid 가 없다. 이 매핑이 없으면
   * 갱신·해지·환불 알림을 받아도 누구의 구독인지 알 수 없다.
   *
   * 문서 ID 는 토큰의 SHA-256 이다. 토큰 자체를 ID 로 쓰면 색인·로그·오류 메시지에
   * 그대로 남는데, 구매 토큰은 그것만 있으면 Developer API 로 구독을 조회할 수 있는
   * 비밀값이다.
   *
   * 보안 규칙에서 클라이언트 접근을 **전부** 막는다(firestore.rules).
   */
  purchaseTokens: 'purchaseTokens',
} as const;

export const GroupFields = {
  name: 'name',
  ownerUid: 'ownerUid',
  memberUids: 'memberUids',
} as const;

export const MemberFields = {
  displayName: 'displayName',
  consent: 'consent',
  paused: 'paused',
  pushToken: 'pushToken',
  sharing: 'sharing',
} as const;

export const AlertFields = {
  senderUid: 'senderUid',
  senderName: 'senderName',
  severity: 'severity',
  type: 'type',
  message: 'message',
  sharing: 'sharing',
  createdAt: 'createdAt',
} as const;

export const LocationFields = {
  expiresAt: 'expiresAt',
} as const;

/** `users/{uid}` 의 구독 상태 필드. **서버만 쓴다**(보안 규칙이 앱의 쓰기를 막는다). */
export const UserFields = {
  entitlement: 'entitlement',
} as const;

/**
 * `users/{uid}.entitlement` 아래의 하위 필드.
 *
 * 앱의 `FirestoreSchema.Entitlements` 와 같은 이름이어야 한다. 다르면 앱이 값을 못 읽고
 * 구독 기록이 없는 것으로 본다 — 돈을 낸 사람이 프리미엄을 못 쓴다.
 */
export const EntitlementFields = {
  plan: 'plan',
  state: 'state',
  productId: 'productId',
  expiryTime: 'expiryTime',
  autoRenewing: 'autoRenewing',
  verifiedAt: 'verifiedAt',
} as const;

/**
 * 구독 상태 키. 앱의 `EntitlementState.key` 와 **글자 하나까지 같아야 한다.**
 *
 * 다르면 앱이 `unknown` 으로 읽고 프리미엄을 주지 않는다. 안전한 방향이지만
 * 조용히 틀리므로 여기와 Entitlement.kt 를 같이 고쳐야 한다.
 */
export const EntitlementStates = {
  none: 'none',
  pending: 'pending',
  active: 'active',
  canceled: 'canceled',
  inGracePeriod: 'in_grace_period',
  onHold: 'on_hold',
  paused: 'paused',
  expired: 'expired',
  revoked: 'revoked',
  unknown: 'unknown',
} as const;

/** 요금제 이름. 기록용이다 — 앱은 state·productId·expiryTime 으로 직접 판단한다. */
export const PlanNames = {
  free: 'free',
  premium: 'premium',
} as const;

/** 구독 상품 ID. 앱의 `SubscriptionCatalog.PREMIUM_MONTHLY` 와 같아야 한다. */
export const SubscriptionProducts = {
  premiumMonthly: 'premium_monthly',
} as const;

/** FCM 데이터 메시지의 키. 앱의 AnsimMessagingService 와 같아야 한다. */
export const MessageKeys = {
  type: 'type',
  groupId: 'groupId',
  alertId: 'alertId',
  senderName: 'senderName',
  message: 'message',
  sharing: 'sharing',
} as const;

/** 메시지 종류. 앱이 이 값으로 알림 모양을 정한다. */
export const MessageTypes = {
  safetyMessage: 'safety_message',
  shareStarted: 'share_started',
} as const;

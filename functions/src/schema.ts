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

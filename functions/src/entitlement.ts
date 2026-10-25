// 구독 상태를 판단하는 **순수 함수**들.
//
// 4단계의 messages.ts 와 같은 방식이다. Play Developer API 응답과 실시간 개발자
// 알림(RTDN)을 받아 'Firestore 에 무엇을 쓸지'를 정하는 부분만 떼어 두었고,
// 에뮬레이터나 네트워크 없이 `node --test` 로 돌린다. index.ts 는 API 를 부르고
// 결과를 이 함수들에 넘겨 Firestore 에 쓰는 배선뿐이다.
//
// 이름과 뜻은 공식 문서를 그대로 따랐다.
//   purchases.subscriptionsv2.get → SubscriptionPurchaseV2
//   RTDN → DeveloperNotification / SubscriptionNotification

import { EntitlementFields, EntitlementStates, PlanNames } from './schema';

/** Play 가 돌려주는 구독 상태(SubscriptionState). */
export type SubscriptionState =
  | 'SUBSCRIPTION_STATE_UNSPECIFIED'
  | 'SUBSCRIPTION_STATE_PENDING'
  | 'SUBSCRIPTION_STATE_ACTIVE'
  | 'SUBSCRIPTION_STATE_PAUSED'
  | 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'
  | 'SUBSCRIPTION_STATE_ON_HOLD'
  | 'SUBSCRIPTION_STATE_CANCELED'
  | 'SUBSCRIPTION_STATE_EXPIRED'
  | 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED';

/** `purchases.subscriptionsv2.get` 응답 중 우리가 쓰는 부분만. */
export interface SubscriptionPurchaseV2 {
  subscriptionState?: string;
  acknowledgementState?: string;
  latestOrderId?: string;
  linkedPurchaseToken?: string;
  testPurchase?: Record<string, unknown>;
  externalAccountIdentifiers?: {
    obfuscatedExternalAccountId?: string;
  };
  lineItems?: Array<{
    productId?: string;
    /** RFC 3339 문자열이다(`2026-11-24T12:00:00Z`). 밀리초가 아니다. */
    expiryTime?: string;
    autoRenewingPlan?: {
      autoRenewEnabled?: boolean;
    };
    offerDetails?: {
      basePlanId?: string;
      offerId?: string;
    };
  }>;
}

/** `users/{uid}.entitlement` 에 쓰는 모양. 앱의 Entitlement.kt 와 짝이다. */
export interface EntitlementDocument {
  plan: string;
  state: string;
  productId: string | null;
  expiryTime: number;
  autoRenewing: boolean;
  verifiedAt: number;
}

/**
 * Play 의 상태를 우리 상태 키로 바꾼다.
 *
 * 앱의 `EntitlementState.key` 와 **글자 하나까지 같아야 한다.** 다르면 앱이
 * `unknown` 으로 읽고 프리미엄을 주지 않는다(그쪽이 안전한 방향이라 조용히 틀린다).
 *
 * 모르는 값은 `unknown` 이다. Play 에 새 상태가 생겼을 때 'active 로 보고 열어 주는'
 * 일이 생기면 안 된다.
 */
export function toEntitlementState(state: string | undefined): string {
  switch (state) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      return EntitlementStates.active;
    case 'SUBSCRIPTION_STATE_CANCELED':
      return EntitlementStates.canceled;
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return EntitlementStates.inGracePeriod;
    case 'SUBSCRIPTION_STATE_ON_HOLD':
      return EntitlementStates.onHold;
    case 'SUBSCRIPTION_STATE_PAUSED':
      return EntitlementStates.paused;
    case 'SUBSCRIPTION_STATE_EXPIRED':
      return EntitlementStates.expired;
    case 'SUBSCRIPTION_STATE_PENDING':
    case 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED':
      return EntitlementStates.pending;
    default:
      return EntitlementStates.unknown;
  }
}

/**
 * 이 상태가 프리미엄을 주는지.
 *
 * Play 구독 수명 주기 문서를 따랐다. 활성·해지(기간 남음)·유예 기간은 주고,
 * 보류·일시정지·대기·만료·회수는 주지 않는다. 앱의 `EntitlementState.grantsPremium`
 * 과 같은 판단이고, **양쪽에 두는 것이 일부러다** — 앱은 서버 값을 믿지만, 서버가
 * 잘못된 상태를 써도 앱이 한 번 더 막는다.
 */
export function grantsPremium(stateKey: string): boolean {
  return (
    stateKey === EntitlementStates.active ||
    stateKey === EntitlementStates.canceled ||
    stateKey === EntitlementStates.inGracePeriod
  );
}

/**
 * RFC 3339 시각 문자열을 밀리초로. 못 읽으면 0.
 *
 * 0 을 돌려주는 것이 안전한 방향이다. 앱은 `now < expiryTime` 을 보므로 0 이면
 * 프리미엄이 열리지 않는다.
 */
export function toMillis(rfc3339: string | undefined): number {
  if (!rfc3339) return 0;
  const parsed = Date.parse(rfc3339);
  return Number.isNaN(parsed) ? 0 : parsed;
}

/**
 * 구독 응답에서 우리 상품의 항목을 고른다.
 *
 * 묶음 구독(여러 상품이 한 구독에 들어가는 경우)이 있을 수 있어서 상품 ID 로 찾는다.
 * 못 찾으면 첫 항목을 쓴다 — 구독이 하나뿐인 지금은 같은 결과다.
 */
export function pickLineItem(
  purchase: SubscriptionPurchaseV2,
  productId: string,
): NonNullable<SubscriptionPurchaseV2['lineItems']>[number] | undefined {
  const items = purchase.lineItems ?? [];
  return items.find((item) => item.productId === productId) ?? items[0];
}

/**
 * Play 응답을 Firestore 에 쓸 문서로 바꾼다.
 *
 * `plan` 은 기록용이다. 앱은 `state` · `productId` · `expiryTime` 세 가지로 직접
 * 판단한다(`Entitlement.planAt`). 여기에 'premium' 이라고 적혀 있다는 것만으로
 * 프리미엄이 열리지는 않는다.
 *
 * @param nowMillis 지금 시각. 함수에서 Date.now() 를 부르지 않는다(테스트).
 */
export function toEntitlementDocument(
  purchase: SubscriptionPurchaseV2,
  productId: string,
  nowMillis: number,
): EntitlementDocument {
  const stateKey = toEntitlementState(purchase.subscriptionState);
  const item = pickLineItem(purchase, productId);
  const premium = grantsPremium(stateKey);

  return {
    [EntitlementFields.plan]: premium ? PlanNames.premium : PlanNames.free,
    [EntitlementFields.state]: stateKey,
    // 응답에 상품 ID 가 없으면 요청에 쓴 값을 적는다. 그래야 앱이 '아는 상품'으로
    // 알아본다. 둘 다 없으면 null 이고, 그때 앱은 프리미엄을 열지 않는다.
    [EntitlementFields.productId]: item?.productId ?? productId ?? null,
    [EntitlementFields.expiryTime]: toMillis(item?.expiryTime),
    [EntitlementFields.autoRenewing]: item?.autoRenewingPlan?.autoRenewEnabled === true,
    [EntitlementFields.verifiedAt]: nowMillis,
  };
}

/** 구독이 회수된 경우에 쓰는 문서(환불·취소). 기간을 남기지 않는다. */
export function revokedEntitlementDocument(
  productId: string | null,
  nowMillis: number,
): EntitlementDocument {
  return {
    [EntitlementFields.plan]: PlanNames.free,
    [EntitlementFields.state]: EntitlementStates.revoked,
    [EntitlementFields.productId]: productId,
    [EntitlementFields.expiryTime]: 0,
    [EntitlementFields.autoRenewing]: false,
    [EntitlementFields.verifiedAt]: nowMillis,
  };
}

/** 구매가 Play 에서 확인(acknowledge)되었는지. */
export function isAcknowledged(purchase: SubscriptionPurchaseV2): boolean {
  return purchase.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
}

// ---- 실시간 개발자 알림(RTDN) ----

/** RTDN 의 구독 알림 종류. 숫자는 공식 문서의 notificationType 이다. */
export const SubscriptionNotificationType = {
  recovered: 1,
  renewed: 2,
  canceled: 3,
  purchased: 4,
  onHold: 5,
  inGracePeriod: 6,
  restarted: 7,
  deferred: 9,
  paused: 10,
  pauseScheduleChanged: 11,
  revoked: 12,
  expired: 13,
  pendingPurchaseCanceled: 20,
} as const;

/** Pub/Sub 으로 오는 알림 본문. */
export interface DeveloperNotification {
  version?: string;
  packageName?: string;
  eventTimeMillis?: string | number;
  subscriptionNotification?: {
    version?: string;
    notificationType?: number;
    purchaseToken?: string;
  };
  voidedPurchaseNotification?: {
    purchaseToken?: string;
    orderId?: string;
    productType?: number;
    refundType?: number;
  };
  oneTimeProductNotification?: Record<string, unknown>;
  testNotification?: Record<string, unknown>;
}

/** RTDN 을 받아 서버가 할 일. */
export type NotificationAction =
  | { kind: 'ignore'; reason: string }
  | { kind: 'reverify'; purchaseToken: string }
  | { kind: 'revoke'; purchaseToken: string };

/**
 * 알림을 보고 무엇을 할지 정한다.
 *
 * ## 거의 모두 '다시 물어본다'로 보낸다
 * 알림의 `notificationType` 만 보고 Firestore 를 고치지 않는다. 공식 문서가
 * '알림을 받으면 Developer API 를 불러 완전한 구매 정보를 가져오라'고 못박고 있고,
 * 알림은 순서가 보장되지 않는다. 늦게 도착한 '갱신됨'으로 이미 해지된 구독을
 * 되살리면 안 된다. **지금 상태를 Play 에 다시 물어보는 쪽이 항상 맞다.**
 *
 * ## 회수만 예외다
 * 환불·회수(`revoked`, `voidedPurchaseNotification`)는 즉시 막아야 한다
 * (Play 문서: '즉시 권한을 회수하라'). 다시 물어보는 동안 열려 있는 틈을 두지 않는다.
 *
 * ## 패키지가 다르면 버린다
 * 같은 Pub/Sub 주제에 다른 앱의 알림이 올 수 있다(주제를 공유한 경우).
 *
 * @param expectedPackageName 우리 앱의 패키지. 비어 있으면 검사하지 않는다.
 */
export function notificationAction(
  notification: DeveloperNotification | undefined,
  expectedPackageName: string,
): NotificationAction {
  if (!notification) return { kind: 'ignore', reason: '본문이 없다' };

  if (
    expectedPackageName &&
    notification.packageName &&
    notification.packageName !== expectedPackageName
  ) {
    return { kind: 'ignore', reason: '다른 앱의 알림이다' };
  }

  if (notification.testNotification) {
    // Play Console 의 '테스트 알림 보내기'. 구매 토큰이 없다.
    return { kind: 'ignore', reason: '테스트 알림이다' };
  }

  const voided = notification.voidedPurchaseNotification;
  if (voided?.purchaseToken) {
    return { kind: 'revoke', purchaseToken: voided.purchaseToken };
  }

  const subscription = notification.subscriptionNotification;
  if (!subscription?.purchaseToken) {
    return { kind: 'ignore', reason: '구독 알림이 아니다' };
  }

  if (subscription.notificationType === SubscriptionNotificationType.revoked) {
    return { kind: 'revoke', purchaseToken: subscription.purchaseToken };
  }

  return { kind: 'reverify', purchaseToken: subscription.purchaseToken };
}

/**
 * Pub/Sub 메시지의 base64 본문을 읽는다.
 *
 * 못 읽으면 undefined 다. 예외를 던지지 않는 이유: 던지면 Pub/Sub 이 같은 메시지를
 * 끝없이 다시 보낸다. 읽을 수 없는 메시지는 다시 보내도 읽을 수 없다.
 */
export function decodeNotification(base64: string | undefined): DeveloperNotification | undefined {
  if (!base64) return undefined;
  try {
    const json = Buffer.from(base64, 'base64').toString('utf8');
    const parsed = JSON.parse(json) as unknown;
    if (!parsed || typeof parsed !== 'object') return undefined;
    return parsed as DeveloperNotification;
  } catch {
    return undefined;
  }
}

/**
 * 구매가 이 계정의 것인지 확인한다.
 *
 * 앱이 결제할 때 `obfuscatedAccountId` 로 uid 의 SHA-256 을 넘긴다. 같은 방법으로
 * 계산한 값과 맞춰 본다.
 *
 * ## 값이 없으면 통과시킨다
 * Play 가 그 값을 늘 돌려주지는 않는다(예전 구매, 다른 경로로 생긴 구매, 묶음 구독).
 * 없는 것을 '남의 구매'로 보면 **정당한 구독자를 막는다.** 토큰 자체가 Play 에서
 * 그 사용자의 기기로 간 비밀값이고, 토큰을 아는 것만으로도 상당한 근거다.
 *
 * ## 값이 있는데 다르면 막는다
 * 남의 구매 토큰을 주워 와 자기 계정에 붙이려는 경우다. 이때는 분명히 막는다.
 */
export function accountMatches(
  purchase: SubscriptionPurchaseV2,
  expectedObfuscatedAccountId: string,
): boolean {
  const actual = purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (!actual) return true;
  if (!expectedObfuscatedAccountId) return true;
  return actual === expectedObfuscatedAccountId;
}

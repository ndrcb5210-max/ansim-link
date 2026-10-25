// entitlement.ts 의 순수 함수 단위 테스트.
//
// 실행: functions 에서 `npm test` (에뮬레이터·네트워크가 필요 없다).
//
// 여기서 지키려는 것은 하나다. **틀릴 때는 막는 방향으로 틀린다.** 모르는 상태,
// 못 읽은 시각, 빈 응답은 모두 '프리미엄이 아닌 쪽'으로 떨어져야 한다.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  type DeveloperNotification,
  type SubscriptionPurchaseV2,
  SubscriptionNotificationType,
  accountMatches,
  decodeNotification,
  grantsPremium,
  isAcknowledged,
  notificationAction,
  pickLineItem,
  revokedEntitlementDocument,
  toEntitlementDocument,
  toEntitlementState,
  toMillis,
} from './entitlement';
import { EntitlementStates, PlanNames, SubscriptionProducts } from './schema';

const NOW = 1_800_000_000_000;
const PRODUCT = SubscriptionProducts.premiumMonthly;
const PACKAGE = 'io.github.ndrcb5210max.ansimlink';

/** 활성 구독 응답 한 건. */
const activePurchase = (overrides: Partial<SubscriptionPurchaseV2> = {}): SubscriptionPurchaseV2 => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  latestOrderId: 'GPA.1234-5678-9012-34567',
  lineItems: [
    {
      productId: PRODUCT,
      expiryTime: '2027-01-15T03:04:05Z',
      autoRenewingPlan: { autoRenewEnabled: true },
      offerDetails: { basePlanId: 'monthly' },
    },
  ],
  ...overrides,
});

describe('상태 변환', () => {
  it('Play 의 상태를 우리 키로 바꾼다', () => {
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_ACTIVE'), EntitlementStates.active);
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_CANCELED'), EntitlementStates.canceled);
    assert.equal(
      toEntitlementState('SUBSCRIPTION_STATE_IN_GRACE_PERIOD'),
      EntitlementStates.inGracePeriod,
    );
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_ON_HOLD'), EntitlementStates.onHold);
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_PAUSED'), EntitlementStates.paused);
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_EXPIRED'), EntitlementStates.expired);
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_PENDING'), EntitlementStates.pending);
    assert.equal(
      toEntitlementState('SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED'),
      EntitlementStates.pending,
    );
  });

  it('모르는 상태는 unknown 이다', () => {
    // Play 에 새 상태가 생겼을 때 active 로 보고 열어 주면 안 된다.
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_SOMETHING_NEW'), EntitlementStates.unknown);
    assert.equal(toEntitlementState('SUBSCRIPTION_STATE_UNSPECIFIED'), EntitlementStates.unknown);
    assert.equal(toEntitlementState(undefined), EntitlementStates.unknown);
  });

  it('프리미엄을 주는 상태는 셋뿐이다', () => {
    // Play 구독 수명 주기 문서를 따랐다. 앱의 EntitlementState.grantsPremium 과 같다.
    assert.equal(grantsPremium(EntitlementStates.active), true);
    assert.equal(grantsPremium(EntitlementStates.canceled), true);
    assert.equal(grantsPremium(EntitlementStates.inGracePeriod), true);

    assert.equal(grantsPremium(EntitlementStates.onHold), false);
    assert.equal(grantsPremium(EntitlementStates.paused), false);
    assert.equal(grantsPremium(EntitlementStates.pending), false);
    assert.equal(grantsPremium(EntitlementStates.expired), false);
    assert.equal(grantsPremium(EntitlementStates.revoked), false);
    assert.equal(grantsPremium(EntitlementStates.unknown), false);
    assert.equal(grantsPremium(EntitlementStates.none), false);
  });
});

describe('시각 변환', () => {
  it('RFC 3339 문자열을 밀리초로 바꾼다', () => {
    assert.equal(toMillis('2027-01-15T03:04:05Z'), Date.parse('2027-01-15T03:04:05Z'));
  });

  it('못 읽으면 0 이다', () => {
    // 0 이면 앱의 'now < expiryTime' 검사를 통과하지 못한다 — 막는 방향이다.
    assert.equal(toMillis(undefined), 0);
    assert.equal(toMillis(''), 0);
    assert.equal(toMillis('어제'), 0);
  });
});

describe('문서 만들기', () => {
  it('활성 구독을 프리미엄으로 쓴다', () => {
    const document = toEntitlementDocument(activePurchase(), PRODUCT, NOW);

    assert.equal(document.plan, PlanNames.premium);
    assert.equal(document.state, EntitlementStates.active);
    assert.equal(document.productId, PRODUCT);
    assert.equal(document.expiryTime, Date.parse('2027-01-15T03:04:05Z'));
    assert.equal(document.autoRenewing, true);
    assert.equal(document.verifiedAt, NOW);
  });

  it('해지했지만 기간이 남은 구독도 프리미엄이다', () => {
    const document = toEntitlementDocument(
      activePurchase({
        subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
        lineItems: [
          {
            productId: PRODUCT,
            expiryTime: '2027-01-15T03:04:05Z',
            autoRenewingPlan: { autoRenewEnabled: false },
          },
        ],
      }),
      PRODUCT,
      NOW,
    );

    assert.equal(document.plan, PlanNames.premium);
    assert.equal(document.state, EntitlementStates.canceled);
    // 앱이 '해지 예약됨 · 날짜까지 쓸 수 있습니다'로 적는 근거다.
    assert.equal(document.autoRenewing, false);
  });

  it('보류는 무료로 쓴다', () => {
    const document = toEntitlementDocument(
      activePurchase({ subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' }),
      PRODUCT,
      NOW,
    );

    assert.equal(document.plan, PlanNames.free);
    assert.equal(document.state, EntitlementStates.onHold);
  });

  it('응답이 비어 있으면 unknown 무료다', () => {
    const document = toEntitlementDocument({}, PRODUCT, NOW);

    assert.equal(document.plan, PlanNames.free);
    assert.equal(document.state, EntitlementStates.unknown);
    assert.equal(document.expiryTime, 0);
    assert.equal(document.autoRenewing, false);
    // 요청에 쓴 상품 ID 를 적어 둔다. 그래야 앱이 '아는 상품'으로 알아본다.
    assert.equal(document.productId, PRODUCT);
  });

  it('autoRenewEnabled 가 없으면 false 로 본다', () => {
    const document = toEntitlementDocument(
      activePurchase({
        lineItems: [{ productId: PRODUCT, expiryTime: '2027-01-15T03:04:05Z' }],
      }),
      PRODUCT,
      NOW,
    );

    assert.equal(document.autoRenewing, false);
  });

  it('회수 문서는 기간을 남기지 않는다', () => {
    const document = revokedEntitlementDocument(PRODUCT, NOW);

    assert.equal(document.plan, PlanNames.free);
    assert.equal(document.state, EntitlementStates.revoked);
    assert.equal(document.expiryTime, 0);
    assert.equal(document.autoRenewing, false);
  });

  it('묶음 구독에서 우리 상품을 골라낸다', () => {
    const purchase = activePurchase({
      lineItems: [
        { productId: 'other_product', expiryTime: '2030-01-01T00:00:00Z' },
        { productId: PRODUCT, expiryTime: '2027-01-15T03:04:05Z' },
      ],
    });

    assert.equal(pickLineItem(purchase, PRODUCT)?.productId, PRODUCT);
    assert.equal(
      toEntitlementDocument(purchase, PRODUCT, NOW).expiryTime,
      Date.parse('2027-01-15T03:04:05Z'),
    );
  });

  it('우리 상품이 없으면 첫 항목을 쓴다', () => {
    const purchase = activePurchase({
      lineItems: [{ productId: 'other_product', expiryTime: '2030-01-01T00:00:00Z' }],
    });

    assert.equal(pickLineItem(purchase, PRODUCT)?.productId, 'other_product');
  });

  it('확인 상태를 읽는다', () => {
    assert.equal(isAcknowledged(activePurchase()), true);
    assert.equal(
      isAcknowledged(activePurchase({ acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' })),
      false,
    );
    assert.equal(isAcknowledged({}), false);
  });
});

describe('계정 확인', () => {
  it('식별값이 같으면 통과한다', () => {
    const purchase = activePurchase({
      externalAccountIdentifiers: { obfuscatedExternalAccountId: 'abc123' },
    });

    assert.equal(accountMatches(purchase, 'abc123'), true);
  });

  it('식별값이 다르면 막는다', () => {
    // 남의 구매 토큰을 자기 계정에 붙이려는 경우다.
    const purchase = activePurchase({
      externalAccountIdentifiers: { obfuscatedExternalAccountId: 'abc123' },
    });

    assert.equal(accountMatches(purchase, 'zzz999'), false);
  });

  it('Play 가 식별값을 주지 않으면 통과시킨다', () => {
    // 없는 것을 '남의 구매'로 보면 정당한 구독자를 막는다(예전 구매·묶음 구독).
    assert.equal(accountMatches(activePurchase(), 'abc123'), true);
    assert.equal(
      accountMatches(activePurchase({ externalAccountIdentifiers: {} }), 'abc123'),
      true,
    );
  });
});

describe('실시간 개발자 알림', () => {
  const notification = (
    overrides: Partial<DeveloperNotification> = {},
  ): DeveloperNotification => ({
    version: '1.0',
    packageName: PACKAGE,
    eventTimeMillis: '1800000000000',
    ...overrides,
  });

  it('구독 알림은 다시 물어본다', () => {
    // 알림 종류만 보고 Firestore 를 고치지 않는다. 알림은 순서가 보장되지 않아서
    // 늦게 온 '갱신됨'으로 해지된 구독을 되살릴 수 있다.
    for (const type of [
      SubscriptionNotificationType.purchased,
      SubscriptionNotificationType.renewed,
      SubscriptionNotificationType.recovered,
      SubscriptionNotificationType.canceled,
      SubscriptionNotificationType.onHold,
      SubscriptionNotificationType.inGracePeriod,
      SubscriptionNotificationType.paused,
      SubscriptionNotificationType.expired,
      SubscriptionNotificationType.restarted,
      SubscriptionNotificationType.deferred,
    ]) {
      const action = notificationAction(
        notification({
          subscriptionNotification: { notificationType: type, purchaseToken: 'token-1' },
        }),
        PACKAGE,
      );
      assert.deepEqual(action, { kind: 'reverify', purchaseToken: 'token-1' });
    }
  });

  it('회수는 즉시 막는다', () => {
    // 다시 물어보는 동안 열려 있는 틈을 두지 않는다(Play 문서: 즉시 회수하라).
    const action = notificationAction(
      notification({
        subscriptionNotification: {
          notificationType: SubscriptionNotificationType.revoked,
          purchaseToken: 'token-1',
        },
      }),
      PACKAGE,
    );

    assert.deepEqual(action, { kind: 'revoke', purchaseToken: 'token-1' });
  });

  it('환불 알림도 즉시 막는다', () => {
    const action = notificationAction(
      notification({
        voidedPurchaseNotification: {
          purchaseToken: 'token-1',
          orderId: 'GS.0000-0000-0000',
          productType: 1,
          refundType: 1,
        },
      }),
      PACKAGE,
    );

    assert.deepEqual(action, { kind: 'revoke', purchaseToken: 'token-1' });
  });

  it('다른 앱의 알림은 버린다', () => {
    const action = notificationAction(
      notification({
        packageName: 'com.example.other',
        subscriptionNotification: {
          notificationType: SubscriptionNotificationType.renewed,
          purchaseToken: 'token-1',
        },
      }),
      PACKAGE,
    );

    assert.equal(action.kind, 'ignore');
  });

  it('테스트 알림은 버린다', () => {
    const action = notificationAction(
      notification({ testNotification: { version: '1.0' } }),
      PACKAGE,
    );

    assert.equal(action.kind, 'ignore');
  });

  it('일회성 상품 알림은 버린다', () => {
    // 이 앱에는 일회성 상품이 없다.
    const action = notificationAction(
      notification({ oneTimeProductNotification: { notificationType: 1 } }),
      PACKAGE,
    );

    assert.equal(action.kind, 'ignore');
  });

  it('구매 토큰이 없으면 버린다', () => {
    const action = notificationAction(
      notification({ subscriptionNotification: { notificationType: 2 } }),
      PACKAGE,
    );

    assert.equal(action.kind, 'ignore');
  });

  it('본문이 없으면 버린다', () => {
    assert.equal(notificationAction(undefined, PACKAGE).kind, 'ignore');
  });

  it('패키지 검사를 끄면 통과시킨다', () => {
    // 배포 전에 packageName 설정을 비워 둔 경우. 알림을 버리는 것보다 처리하는 쪽이
    // 낫다(상태를 다시 물어보는 것뿐이라 위험하지 않다).
    const action = notificationAction(
      notification({
        packageName: 'com.example.other',
        subscriptionNotification: { notificationType: 2, purchaseToken: 'token-1' },
      }),
      '',
    );

    assert.deepEqual(action, { kind: 'reverify', purchaseToken: 'token-1' });
  });
});

describe('Pub/Sub 본문 읽기', () => {
  it('base64 JSON 을 읽는다', () => {
    const payload = {
      version: '1.0',
      packageName: PACKAGE,
      subscriptionNotification: { notificationType: 4, purchaseToken: 'token-1' },
    };
    const base64 = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64');

    assert.deepEqual(decodeNotification(base64), payload);
  });

  it('읽을 수 없는 본문은 undefined 다', () => {
    // 예외를 던지면 Pub/Sub 이 같은 메시지를 끝없이 다시 보낸다.
    assert.equal(decodeNotification(undefined), undefined);
    assert.equal(decodeNotification(''), undefined);
    assert.equal(decodeNotification('JSON 이 아니다'), undefined);
    assert.equal(
      decodeNotification(Buffer.from('"문자열"', 'utf8').toString('base64')),
      undefined,
    );
  });
});

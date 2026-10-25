// firestore.rules 규칙 테스트 — 5단계(구독 결제)
//
// 실행: tests/rules 에서 `npm test` (다른 테스트 파일과 같이 돌아간다).
//
// **세 테스트 파일은 한 번에 하나씩 돌려야 한다.** 같은 에뮬레이터 DB 를 쓰고
// beforeEach 마다 clearFirestore() 를 부르기 때문이다. package.json 의 test 스크립트에
// `--test-concurrency=1` 이 들어 있다(4단계 '막힌 점' 1번).
//
// 여기서 확인하는 것은 하나다. **클라이언트는 users/{uid}.entitlement 를 쓸 수 없다.**
// 그 값이 프리미엄을 여는 근거라서(CLAUDE.md 3장) 앱이 쓸 수 있으면 APK 를 고친 사람이
// 결제 없이 프리미엄을 쓴다. 앱 코드에 그 쓰기가 없다는 것만으로는 모자라다 —
// 규칙이 막아야 한다.
//
// 동시에 **막지 말아야 할 것을 막지 않는지**도 본다(4단계 '막힌 점' 2번에서 배운 것이다).
// 서버가 entitlement 를 써 준 뒤에도 사용자가 자기 이름·동의 기록을 고칠 수 있어야 하고,
// 계정 삭제도 되어야 한다.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const PROJECT_ID = 'demo-ansim-link';

const inOneMonth = () => Date.now() + 30 * 24 * 60 * 60 * 1000;

/** 서버가 쓰는 모양의 엔타이틀먼트. functions/src/entitlement.ts 와 같은 필드다. */
const entitlementField = (extra = {}) => ({
  plan: 'premium',
  state: 'active',
  productId: 'premium_monthly',
  expiryTime: inOneMonth(),
  autoRenewing: true,
  verifiedAt: Date.now(),
  ...extra,
});

/** 가입할 때 앱이 쓰는 모양의 사용자 문서. entitlement 가 들어 있지 않다. */
const userDocument = (extra = {}) => ({
  email: 'hong@example.com',
  displayName: '민수',
  consents: {
    terms: { accepted: true, version: '2026-10-24', at: Date.now() },
  },
  createdAt: Date.now(),
  ...extra,
});

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

/** 규칙을 끄고 바탕 데이터를 깔아 둔다(서버가 쓴 상태를 흉내 낸다). */
async function seed(write) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await write(context.firestore());
  });
}

describe('구독 상태 쓰기 막기', () => {
  it('가입할 때 entitlement 없이 사용자 문서를 만든다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(setDoc(doc(db, 'users', 'me'), userDocument()));
  });

  it('사용자 문서를 만들면서 entitlement 를 끼워 넣을 수 없다', async () => {
    // 가입 요청 한 번으로 프리미엄을 얻으려는 경우다.
    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() })),
    );
  });

  it('없던 entitlement 를 나중에 써 넣을 수 없다', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument());
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      updateDoc(doc(db, 'users', 'me'), { entitlement: entitlementField() }),
    );
  });

  it('서버가 써 둔 entitlement 를 고칠 수 없다', async () => {
    // 만료된 구독의 기간을 10년 뒤로 미루려는 경우다.
    await seed(async (db) => {
      await setDoc(
        doc(db, 'users', 'me'),
        userDocument({
          entitlement: entitlementField({ state: 'expired', expiryTime: Date.now() - 1000 }),
        }),
      );
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      updateDoc(doc(db, 'users', 'me'), {
        entitlement: entitlementField({ expiryTime: Date.now() + 10 * 365 * 86400000 }),
      }),
    );
  });

  it('entitlement 의 하위 필드 하나만 고치려 해도 막는다', async () => {
    // 점 표기법으로 state 만 바꾸는 경우. diff 의 affectedKeys 는 최상위 키를 주므로
    // 이것도 'entitlement 가 바뀌었다'로 잡힌다.
    await seed(async (db) => {
      await setDoc(
        doc(db, 'users', 'me'),
        userDocument({ entitlement: entitlementField({ state: 'expired' }) }),
      );
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(updateDoc(doc(db, 'users', 'me'), { 'entitlement.state': 'active' }));
  });

  it('entitlement 를 지울 수도 없다', async () => {
    // 지우는 것으로는 프리미엄을 얻지 못하지만(문서가 없으면 무료다), '서버만 쓴다'를
    // 규칙으로 말하려면 지우기도 막아야 한다. 환불 뒤 revoked 를 지워서 재검증을
    // 유도하는 식의 장난도 막힌다.
    await seed(async (db) => {
      await setDoc(
        doc(db, 'users', 'me'),
        userDocument({ entitlement: entitlementField() }),
      );
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      setDoc(doc(db, 'users', 'me'), userDocument()), // merge 가 아닌 덮어쓰기
    );
  });

  it('남의 구독 상태는 읽지도 쓰지도 못한다', async () => {
    await seed(async (db) => {
      await setDoc(
        doc(db, 'users', 'other'),
        userDocument({ entitlement: entitlementField() }),
      );
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(getDoc(doc(db, 'users', 'other')));
    await assertFails(
      updateDoc(doc(db, 'users', 'other'), { entitlement: entitlementField() }),
    );
  });

  it('로그인하지 않으면 아무것도 못 한다', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'users', 'me')));
    await assertFails(updateDoc(doc(db, 'users', 'me'), { entitlement: entitlementField() }));
  });
});

describe('막지 말아야 할 것', () => {
  it('내 구독 상태는 읽는다', async () => {
    // 앱이 프리미엄을 여는 근거다. 읽기를 막으면 결제한 사람이 프리미엄을 못 쓴다.
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(getDoc(doc(db, 'users', 'me')));
  });

  it('entitlement 가 있어도 이름을 바꿀 수 있다', async () => {
    // 4단계 '막힌 점' 2번과 같은 함정이다. update 의 request.resource.data 는 쓰고 난 뒤의
    // 문서 전체라서, 'entitlement 가 없어야 한다'로 규칙을 썼다면 서버가 그 필드를 써 준
    // 뒤부터 사용자가 자기 문서를 하나도 고칠 수 없게 된다.
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(updateDoc(doc(db, 'users', 'me'), { displayName: '민수2' }));
  });

  it('entitlement 가 있어도 동의 기록을 갱신할 수 있다', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(
      setDoc(
        doc(db, 'users', 'me'),
        { consents: { terms: { accepted: true, version: '2026-11-01', at: Date.now() } } },
        { merge: true },
      ),
    );
  });

  it('entitlement 가 있어도 계정을 삭제할 수 있다', async () => {
    // 구독 중이어도 계정 삭제를 막으면 안 된다(Google Play 계정 삭제 정책).
    // 구독 자체는 Play 에서 해지해야 하고, 화면이 그 안내와 링크를 보여 준다.
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(deleteDoc(doc(db, 'users', 'me')));
  });

  it('푸시 토큰은 그대로 읽고 쓴다', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users', 'me'), userDocument({ entitlement: entitlementField() }));
    });

    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'users', 'me', 'pushTokens', 'token-1'), {
        platform: 'android',
        updatedAt: Date.now(),
      }),
    );
  });
});

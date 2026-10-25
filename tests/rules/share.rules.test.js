// firestore.rules 규칙 테스트 — 4단계(위치 공유와 안심 메시지)
//
// 실행: tests/rules 에서 `npm test` (firestore.rules.test.js 와 같이 돌아간다).
//
// 2단계 테스트와 파일을 나눈 이유: 한 파일이 900줄을 넘어가면 어느 묶음이 무엇을
// 확인하는지 찾기 어렵다. 바탕 데이터를 깔는 도우미는 두 파일이 거의 같은 모양이라
// 여기에 다시 적었다(테스트 파일끼리 공유 모듈을 두면 그 모듈이 또 하나의
// '테스트되지 않는 코드'가 된다).
//
// **두 테스트 파일은 한 번에 하나씩 돌려야 한다.** `node --test` 는 기본으로 파일을
// 여러 개 동시에 돌리는데, 두 파일이 같은 에뮬레이터 DB 를 쓰고 beforeEach 마다
// clearFirestore() 를 부른다. 동시에 돌면 한쪽이 다른 쪽 데이터를 지워 버려서
// 엉뚱한 실패가 난다('Null value error', '막아야 할 쓰기가 통과했다' 등).
// 그래서 package.json 의 test 스크립트에 `--test-concurrency=1` 을 넣었다.
//
// 여기서 가장 중요한 것은 **동의하지 않았거나 일시중지 중인 사람의 위치가 서버에
// 올라가지 못한다**는 것이다(CLAUDE.md 원칙 1). 앱 코드에 버그가 있어도 규칙이 막아야 한다.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-ansim-link';
const GROUP_ID = 'group1';

const inOneDay = () => Date.now() + 24 * 60 * 60 * 1000;
const anHourAgo = () => Date.now() - 60 * 60 * 1000;

const memberDocument = (displayName, extra = {}) => ({
  displayName,
  consent: { location: true, version: '2026-10-24', at: Date.now() },
  paused: false,
  ...extra,
});

/** 유효한 위치 문서 한 건. 규칙이 보는 필드만 담는다. */
const locationDocument = (extra = {}) => ({
  lat: 37.5665,
  lng: 126.978,
  accuracy: 18,
  at: Date.now(),
  sessionId: 'session-1',
  expiresAt: inOneDay(),
  ...extra,
});

/** 유효한 안심 메시지 한 건. */
const alertDocument = (senderUid, extra = {}) => ({
  senderUid,
  senderName: '민수',
  severity: 'emergency',
  type: 'earthquake',
  message: '재난문자 받았어. 나는 괜찮아.',
  sharing: true,
  createdAt: Date.now(),
  ...extra,
});

/** 유효한 제공 내역 한 건. 만들 때 endedAt 은 반드시 null 이어야 한다. */
const provisionLogDocument = (uid, extra = {}) => ({
  uid,
  sessionId: 'session-1',
  purpose: 'alert_prompt',
  receiverUids: ['owner'],
  startedAt: Date.now(),
  plannedEndAt: Date.now() + 60 * 60 * 1000,
  endedAt: null,
  ...extra,
});

/** 공유 중 표시 한 건. */
const sharingField = (extra = {}) => ({
  active: true,
  startedAt: Date.now(),
  endsAt: Date.now() + 60 * 60 * 1000,
  sessionId: 'session-1',
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

/** 규칙을 끄고 바탕 데이터를 깔아 둔다. */
async function seed(write) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await write(context.firestore());
  });
}

async function seedGroup({ ownerUid = 'owner', memberUids = ['owner'] } = {}) {
  await seed(async (db) => {
    await setDoc(doc(db, 'groups', GROUP_ID), {
      name: '우리 가족',
      ownerUid,
      memberUids,
      createdAt: Date.now(),
    });
    for (const uid of memberUids) {
      await setDoc(doc(db, 'groups', GROUP_ID, 'members', uid), memberDocument(uid));
    }
  });
}

describe('위치 쓰기', () => {
  it('동의했고 일시중지가 아니면 본인 위치를 쓴다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument()),
    );
  });

  it('일시중지 중이면 위치를 쓸 수 없다', async () => {
    // 위치정보법 제24조의 '수집 일시중지'다. 앱 코드가 틀려도 규칙에서 막아야 한다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'member'),
        memberDocument('member', { paused: true }),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument()),
    );
  });

  it('동의를 철회하면 위치를 쓸 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), {
        displayName: 'member',
        consent: { location: false, version: '2026-10-24', at: Date.now() },
        paused: false,
      });
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument()),
    );
  });

  it('구성원 문서가 없으면 위치를 쓸 수 없다', async () => {
    // 그룹 목록(memberUids)에는 있는데 구성원 문서가 없는 어긋난 상태.
    // 2단계에서 그룹 참여를 두 번에 나눠 쓰므로 중간에 끊기면 이 상태가 될 수 있다.
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID), {
        name: '우리 가족',
        ownerUid: 'owner',
        memberUids: ['owner', 'ghost'],
        createdAt: Date.now(),
      });
    });

    const db = testEnv.authenticatedContext('ghost').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'ghost'), locationDocument()),
    );
  });

  it('남의 위치를 쓸 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'owner'), locationDocument()),
    );
  });

  it('그룹 구성원은 남의 위치를 읽는다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument());
    });

    const db = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(getDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member')));
  });

  it('구성원이 아니면 위치를 읽을 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument());
    });

    const db = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(getDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member')));
  });

  it('좌표 범위를 벗어나면 쓸 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument({ lat: 999 })),
    );
  });

  it('모르는 필드를 끼워 넣을 수 없다', async () => {
    // 위치 문서를 쪽지처럼 다른 용도로 쓰는 것을 막는다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'locations', 'member'),
        locationDocument({ memo: '여기로 와' }),
      ),
    );
  });

  it('본인은 자기 위치를 지울 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member'), locationDocument());
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(deleteDoc(doc(db, 'groups', GROUP_ID, 'locations', 'member')));
  });
});

describe('공유 중 표시와 푸시 토큰', () => {
  it('본인은 공유 중 표시를 켤 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), { sharing: sharingField() }),
    );
  });

  it('종료 시각을 185분보다 멀리 둘 수 없다', async () => {
    // 이 값이 '지금 공유 중인가'의 근거다(GroupMember.sharingNow).
    // 10년 뒤를 적어 두고 영원히 '공유 중'으로 보이게 만드는 것을 막는다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), {
        sharing: sharingField({ endsAt: Date.now() + 365 * 24 * 60 * 60 * 1000 }),
      }),
    );
  });

  it('공유를 끄는 것은 종료 시각과 상관없이 된다', async () => {
    // 줄이는 방향은 막을 이유가 없다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), {
        sharing: { active: false, startedAt: 0, endsAt: 0, sessionId: '' },
      }),
    );
  });

  it('공유 표시가 끝난 시각으로 멈춰 있어도 일시중지는 된다', async () => {
    // 앱이 강제 종료되면 sharing.active 가 true 로 남고 endsAt 이 과거가 된다.
    // 그 상태에서 '일시중지'가 막히면 원칙 1(철회·중지는 즉시)을 어긴다.
    // 규칙이 sharing 을 '들어 있으면' 검사하면 이 쓰기가 막힌다. 그래서 '바뀔 때만' 본다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'member'),
        memberDocument('member', {
          sharing: {
            active: true,
            startedAt: anHourAgo() - 60 * 60 * 1000,
            endsAt: anHourAgo(),
            sessionId: 'stale',
          },
        }),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), { paused: true }),
    );
  });

  it('남의 공유 중 표시를 켤 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'owner'), { sharing: sharingField() }),
    );
  });

  it('본인은 푸시 토큰을 등록한다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), { pushToken: 'token-1' }),
    );
    await assertSucceeds(
      setDoc(doc(db, 'users', 'member', 'pushTokens', 'token-1'), {
        platform: 'android',
        updatedAt: Date.now(),
      }),
    );
  });

  it('남의 푸시 토큰 문서는 쓸 수 없다', async () => {
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'users', 'owner', 'pushTokens', 'token-1'), {
        platform: 'android',
        updatedAt: Date.now(),
      }),
    );
  });
});

describe('안심 메시지', () => {
  it('구성원은 자기 이름으로 메시지를 만든다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('member')),
    );
  });

  it('남의 이름으로 메시지를 보낼 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('owner')));
  });

  it('구성원이 아니면 메시지를 만들 수 없다', async () => {
    await seedGroup({ memberUids: ['owner'] });
    const db = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('stranger')),
    );
  });

  it('200자를 넘는 메시지는 막는다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'alerts', 'a1'),
        alertDocument('member', { message: '가'.repeat(201) }),
      ),
    );
  });

  it('빈 메시지는 막는다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'alerts', 'a1'),
        alertDocument('member', { message: '' }),
      ),
    );
  });

  it('보낸 메시지는 고칠 수 없다', async () => {
    // 받은 사람이 본 것과 서버에 남은 것이 달라지면 안 된다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('member'));
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), { message: '바뀐 내용' }),
    );
  });

  it('그룹 구성원은 메시지를 읽는다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('member'));
    });

    const db = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(getDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1')));
  });

  it('받은 사람은 메시지를 지울 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member', 'other'] });
    await seed(async (db) => {
      await setDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1'), alertDocument('member'));
    });

    const db = testEnv.authenticatedContext('other').firestore();
    await assertFails(deleteDoc(doc(db, 'groups', GROUP_ID, 'alerts', 'a1')));
  });
});

describe('위치 제공 내역', () => {
  it('구성원은 자기 제공 내역을 만든다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'), provisionLogDocument('member')),
    );
  });

  it('남의 이름으로 제공 내역을 만들 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'), provisionLogDocument('owner')),
    );
  });

  it('만들 때 endedAt 을 미리 채울 수 없다', async () => {
    // 처음부터 '끝났다'고 적어 두고 위치를 보내는 것을 막는다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member', { endedAt: Date.now() }),
      ),
    );
  });

  it('비어 있는 endedAt 은 한 번 채울 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'), { endedAt: Date.now() }),
    );
  });

  it('이미 채운 endedAt 은 다시 바꿀 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member', { endedAt: anHourAgo() }),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'), { endedAt: Date.now() }),
    );
  });

  it('endedAt 과 함께 다른 값을 바꿀 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'), {
        endedAt: Date.now(),
        receiverUids: [],
      }),
    );
  });

  it('내 세션 내역은 조회할 수 있다', async () => {
    // 앱이 공유를 끝낼 때 이 질의로 찾아 종료 시각을 채운다
    // (FirestoreShareRepository.endSharing). list 를 아예 막으면 그 경로가 깨진다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      getDocs(
        query(
          collection(db, 'groups', GROUP_ID, 'provisionLogs'),
          where('uid', '==', 'member'),
          where('sessionId', '==', 'session-1'),
        ),
      ),
    );
  });

  it('남의 내역을 훑는 질의는 막힌다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const db = testEnv.authenticatedContext('owner').firestore();
    // 조건 없는 전체 조회.
    await assertFails(getDocs(collection(db, 'groups', GROUP_ID, 'provisionLogs')));
    // 남의 uid 로 좁힌 조회.
    await assertFails(
      getDocs(
        query(
          collection(db, 'groups', GROUP_ID, 'provisionLogs'),
          where('uid', '==', 'member'),
        ),
      ),
    );
  });

  it('제공 내역은 본인만 읽는다', async () => {
    // 다른 구성원이 '누가 언제 위치를 제공했는지' 훑을 수 있으면 그것이 감시 도구가 된다
    // (CLAUDE.md 원칙 10). 공유 중이라는 사실은 members.sharing 으로 이미 서로 보인다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const mine = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(getDoc(doc(mine, 'groups', GROUP_ID, 'provisionLogs', 'p1')));

    const theirs = testEnv.authenticatedContext('owner').firestore();
    await assertFails(getDoc(doc(theirs, 'groups', GROUP_ID, 'provisionLogs', 'p1')));
  });

  it('제공 내역은 지울 수 없다', async () => {
    // 위치정보법 제16조② 보관 의무가 있고 좌표가 들어 있지 않다.
    await seedGroup({ memberUids: ['owner', 'member'] });
    await seed(async (db) => {
      await setDoc(
        doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1'),
        provisionLogDocument('member'),
      );
    });

    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(deleteDoc(doc(db, 'groups', GROUP_ID, 'provisionLogs', 'p1')));
  });
});

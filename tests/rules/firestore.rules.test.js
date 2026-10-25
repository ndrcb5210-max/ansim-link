// firestore.rules 규칙 테스트
//
// 실행: tests/rules 에서
//   npm install
//   npm test
// (Firestore 에뮬레이터가 뜬다. Java 17 이상과 firebase-tools 가 필요하다.)
//
// 이 파일은 "앱이 실제로 하는 쓰기 순서"를 그대로 흉내 낸다.
// 특히 그룹 참여는 배치가 아니라 두 번에 나눠 쓴다. 보안 규칙이 배치 안의 쓰기를
// '배치 전 상태'로 평가하기 때문이다.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  arrayRemove,
  arrayUnion,
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-ansim-link';
const GROUP_ID = 'group1';
const CODE = 'KH3M7P';

/** 24시간 뒤 (밀리초). 규칙은 expiresAt 을 밀리초 정수로 본다. */
const inOneDay = () => Date.now() + 24 * 60 * 60 * 1000;
const anHourAgo = () => Date.now() - 60 * 60 * 1000;

const memberDocument = (displayName, extra = {}) => ({
  displayName,
  consent: { location: true, version: '2026-10-24', at: Date.now() },
  paused: false,
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

/** owner 가 만든 그룹 하나. members 는 인자로 받은 uid 들. */
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

async function seedInvite({ expiresAt = inOneDay(), groupId = GROUP_ID } = {}) {
  await seed(async (db) => {
    await setDoc(doc(db, 'inviteCodes', CODE), {
      groupId,
      groupName: '우리 가족',
      memberNames: ['민수'],
      expiresAt,
      createdBy: 'owner',
    });
  });
}

describe('users', () => {
  it('본인 문서는 읽고 쓸 수 있다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(setDoc(doc(db, 'users', 'me'), { displayName: '민수' }));
    await assertSucceeds(getDoc(doc(db, 'users', 'me')));
  });

  it('남의 문서는 읽을 수 없다', async () => {
    await seed(async (db) => setDoc(doc(db, 'users', 'other'), { displayName: '지은' }));
    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(getDoc(doc(db, 'users', 'other')));
  });

  it('로그인하지 않으면 읽을 수 없다', async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'users', 'me')));
  });
});

describe('groups 읽기', () => {
  it('구성원은 그룹을 읽는다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(getDoc(doc(db, 'groups', GROUP_ID)));
  });

  it('구성원이 아니면 그룹을 읽을 수 없다', async () => {
    await seedGroup();
    const db = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(getDoc(doc(db, 'groups', GROUP_ID)));
  });

  it('구성원이 아니면 구성원 목록도 읽을 수 없다', async () => {
    await seedGroup();
    const db = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(getDoc(doc(db, 'groups', GROUP_ID, 'members', 'owner')));
  });
});

describe('groups 만들기', () => {
  it('자기만 구성원으로 넣은 그룹은 만들 수 있다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'groups', 'new1'), {
        name: '우리 가족',
        ownerUid: 'me',
        memberUids: ['me'],
        createdAt: Date.now(),
      }),
    );
  });

  it('남을 구성원으로 미리 넣어 만들 수 없다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', 'new2'), {
        name: '우리 가족',
        ownerUid: 'me',
        memberUids: ['me', 'victim'],
        createdAt: Date.now(),
      }),
    );
  });

  it('남을 소유자로 만들 수 없다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', 'new3'), {
        name: '우리 가족',
        ownerUid: 'other',
        memberUids: ['other'],
        createdAt: Date.now(),
      }),
    );
  });

  it('이름이 비면 만들 수 없다', async () => {
    const db = testEnv.authenticatedContext('me').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', 'new4'), {
        name: '',
        ownerUid: 'me',
        memberUids: ['me'],
        createdAt: Date.now(),
      }),
    );
  });

  it('그룹을 만든 사람은 초대 코드 없이 자기 구성원 문서를 만든다', async () => {
    await seedGroup({ ownerUid: 'owner', memberUids: ['owner'] });
    await seed(async (db) => deleteDoc(doc(db, 'groups', GROUP_ID, 'members', 'owner')));

    const db = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(
      setDoc(doc(db, 'groups', GROUP_ID, 'members', 'owner'), memberDocument('민수')),
    );
  });
});

describe('초대 코드로 참여', () => {
  it('살아 있는 코드가 있으면 구성원 문서를 만들고 목록에 들어갈 수 있다', async () => {
    await seedGroup();
    await seedInvite();
    const db = testEnv.authenticatedContext('joiner').firestore();

    // 1단계: 구성원 문서(코드 검사를 여기서 한다)
    await assertSucceeds(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'joiner'),
        memberDocument('지은', { joinedWithCode: CODE }),
      ),
    );
    // 2단계: memberUids 에 자기 자신 추가
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID), { memberUids: arrayUnion('joiner') }),
    );
  });

  it('만료된 코드로는 구성원 문서를 만들 수 없다', async () => {
    await seedGroup();
    await seedInvite({ expiresAt: anHourAgo() });
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'joiner'),
        memberDocument('지은', { joinedWithCode: CODE }),
      ),
    );
  });

  it('없는 코드로는 구성원 문서를 만들 수 없다', async () => {
    await seedGroup();
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'joiner'),
        memberDocument('지은', { joinedWithCode: 'ZZZZZZ' }),
      ),
    );
  });

  it('다른 그룹의 코드로는 이 그룹에 들어올 수 없다', async () => {
    await seedGroup();
    await seedInvite({ groupId: 'anotherGroup' });
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertFails(
      setDoc(
        doc(db, 'groups', GROUP_ID, 'members', 'joiner'),
        memberDocument('지은', { joinedWithCode: CODE }),
      ),
    );
  });

  it('위치 제공에 동의하지 않으면 구성원이 될 수 없다', async () => {
    await seedGroup();
    await seedInvite();
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertFails(
      setDoc(doc(db, 'groups', GROUP_ID, 'members', 'joiner'), {
        displayName: '지은',
        consent: { location: false, version: '2026-10-24', at: Date.now() },
        paused: false,
        joinedWithCode: CODE,
      }),
    );
  });

  it('구성원 문서 없이 memberUids 에만 끼어들 수 없다', async () => {
    await seedGroup();
    await seedInvite();
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID), { memberUids: arrayUnion('joiner') }),
    );
  });

  it('남을 그룹에 끌어들일 수 없다', async () => {
    await seedGroup();
    await seedInvite();
    const db = testEnv.authenticatedContext('joiner').firestore();
    await seed(async (adminDb) =>
      setDoc(
        doc(adminDb, 'groups', GROUP_ID, 'members', 'joiner'),
        memberDocument('지은', { joinedWithCode: CODE }),
      ),
    );
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID), { memberUids: arrayUnion('victim') }),
    );
  });
});

describe('내 공유 상태', () => {
  it('본인은 일시중지할 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), { paused: true }),
    );
  });

  it('남의 공유 상태는 바꿀 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'owner'), { paused: true }),
    );
  });

  it('그룹을 만든 사람도 남의 공유 상태는 바꿀 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('owner').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID, 'members', 'member'), { paused: true }),
    );
  });

  it('동의 철회: 본인 구성원 문서를 지우고 목록에서 빠질 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertSucceeds(deleteDoc(doc(db, 'groups', GROUP_ID, 'members', 'member')));
    await assertSucceeds(
      updateDoc(doc(db, 'groups', GROUP_ID), { memberUids: arrayRemove('member') }),
    );
  });

  it('남을 그룹에서 뺄 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member', 'other'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID), { memberUids: arrayRemove('other') }),
    );
  });

  it('그룹 이름을 몰래 바꾸면서 나갈 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      updateDoc(doc(db, 'groups', GROUP_ID), {
        memberUids: arrayRemove('member'),
        name: '바뀐 이름',
      }),
    );
  });
});

describe('소유권과 해산', () => {
  it('만든 사람은 남은 구성원에게 소유권을 넘길 수 있다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(updateDoc(doc(db, 'groups', GROUP_ID), { ownerUid: 'member' }));
  });

  it('구성원이 아닌 사람에게는 소유권을 넘길 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('owner').firestore();
    await assertFails(updateDoc(doc(db, 'groups', GROUP_ID), { ownerUid: 'stranger' }));
  });

  it('구성원이 소유권을 가로챌 수 없다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const db = testEnv.authenticatedContext('member').firestore();
    await assertFails(updateDoc(doc(db, 'groups', GROUP_ID), { ownerUid: 'member' }));
  });

  it('만든 사람만 그룹을 해산한다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });
    const memberDb = testEnv.authenticatedContext('member').firestore();
    await assertFails(deleteDoc(doc(memberDb, 'groups', GROUP_ID)));

    const ownerDb = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(deleteDoc(doc(ownerDb, 'groups', GROUP_ID)));
  });
});

describe('inviteCodes', () => {
  it('코드를 알면 단건으로 읽을 수 있다', async () => {
    await seedInvite();
    const db = testEnv.authenticatedContext('joiner').firestore();
    await assertSucceeds(getDoc(doc(db, 'inviteCodes', CODE)));
  });

  it('로그인하지 않으면 읽을 수 없다', async () => {
    await seedInvite();
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'inviteCodes', CODE)));
  });

  it('그룹을 만든 사람만 코드를 발급한다', async () => {
    await seedGroup({ memberUids: ['owner', 'member'] });

    const memberDb = testEnv.authenticatedContext('member').firestore();
    await assertFails(
      setDoc(doc(memberDb, 'inviteCodes', 'AAAAAA'), {
        groupId: GROUP_ID,
        groupName: '우리 가족',
        memberNames: ['민수'],
        expiresAt: inOneDay(),
        createdBy: 'member',
      }),
    );

    const ownerDb = testEnv.authenticatedContext('owner').firestore();
    await assertSucceeds(
      setDoc(doc(ownerDb, 'inviteCodes', 'BBBBBB'), {
        groupId: GROUP_ID,
        groupName: '우리 가족',
        memberNames: ['민수'],
        expiresAt: inOneDay(),
        createdBy: 'owner',
      }),
    );
  });

  it('남의 그룹에 대한 코드를 만들 수 없다', async () => {
    await seedGroup({ ownerUid: 'owner', memberUids: ['owner'] });
    const db = testEnv.authenticatedContext('attacker').firestore();
    await assertFails(
      setDoc(doc(db, 'inviteCodes', 'CCCCCC'), {
        groupId: GROUP_ID,
        groupName: '우리 가족',
        memberNames: [],
        expiresAt: inOneDay(),
        createdBy: 'attacker',
      }),
    );
  });
});

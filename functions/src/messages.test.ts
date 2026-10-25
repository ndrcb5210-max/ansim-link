// messages.ts 의 순수 함수 단위 테스트.
//
// 실행: functions 에서 `npm test` (tsc 로 컴파일한 뒤 node --test 로 돌린다).
// **에뮬레이터가 필요 없다.** 판단이 들어가는 부분을 Firebase 와 떼어 둔 덕분이다.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  DEFAULT_MESSAGE,
  MAX_TOKENS_PER_BATCH,
  SHARING_GRACE_MILLIS,
  type MemberSnapshot,
  chunkTokens,
  isShareJustStarted,
  recipientTokens,
  safetyMessagePayload,
  shareStartedPayload,
  shouldClearSharing,
  shouldDeleteLocation,
} from './messages';
import { MessageKeys, MessageTypes } from './schema';

/** 동의했고 일시중지가 아닌 구성원 하나. */
const member = (uid: string, extra: Partial<MemberSnapshot> = {}): MemberSnapshot => ({
  uid,
  displayName: uid,
  pushToken: `token-${uid}`,
  consent: { location: true },
  paused: false,
  ...extra,
});

const NOW = 1_792_000_000_000;

describe('받는 사람 고르기', () => {
  it('보낸 사람은 뺀다', () => {
    const tokens = recipientTokens([member('me'), member('a'), member('b')], 'me');

    assert.deepEqual(tokens, ['token-a', 'token-b']);
  });

  it('토큰이 없는 사람은 뺀다', () => {
    const tokens = recipientTokens(
      [member('a'), member('b', { pushToken: null }), member('c', { pushToken: '' })],
      'me',
    );

    assert.deepEqual(tokens, ['token-a']);
  });

  it('토큰 필드가 아예 없는 사람도 뺀다', () => {
    const noToken: MemberSnapshot = { uid: 'b', consent: { location: true }, paused: false };
    const tokens = recipientTokens([member('a'), noToken], 'me');

    assert.deepEqual(tokens, ['token-a']);
  });

  it('동의를 철회한 사람은 뺀다', () => {
    const tokens = recipientTokens(
      [member('a'), member('b', { consent: { location: false } })],
      'me',
    );

    assert.deepEqual(tokens, ['token-a']);
  });

  it('동의 필드가 없는 사람은 뺀다', () => {
    // 어긋난 문서다. 동의가 확인되지 않으면 보내지 않는 쪽이 안전하다.
    const tokens = recipientTokens([member('a'), member('b', { consent: null })], 'me');

    assert.deepEqual(tokens, ['token-a']);
  });

  it('일시중지한 사람은 뺀다', () => {
    // 메시지에는 위치가 없지만, '이 그룹에 내 소식을 보내지 않겠다'는 뜻으로 눌렀을
    // 가능성이 높다. 앱(SafetyMessageWorker)도 같은 판단을 한다.
    const tokens = recipientTokens([member('a'), member('b', { paused: true })], 'me');

    assert.deepEqual(tokens, ['token-a']);
  });

  it('같은 토큰이 두 번 들어오면 한 번만 보낸다', () => {
    const tokens = recipientTokens(
      [member('a'), member('b', { pushToken: 'token-a' })],
      'me',
    );

    assert.deepEqual(tokens, ['token-a']);
  });

  it('보낼 사람이 없으면 빈 목록이다', () => {
    assert.deepEqual(recipientTokens([member('me')], 'me'), []);
    assert.deepEqual(recipientTokens([], 'me'), []);
  });
});

describe('토큰 묶기', () => {
  it('500개씩 나눈다', () => {
    const tokens = Array.from({ length: 1_200 }, (_, index) => `t${index}`);

    const chunks = chunkTokens(tokens);

    assert.equal(chunks.length, 3);
    assert.equal(chunks[0]?.length, MAX_TOKENS_PER_BATCH);
    assert.equal(chunks[2]?.length, 200);
  });

  it('무료 그룹 인원이면 한 묶음이다', () => {
    assert.equal(chunkTokens(['a', 'b']).length, 1);
  });

  it('빈 목록이면 묶음도 없다', () => {
    assert.deepEqual(chunkTokens([]), []);
  });

  it('묶음 크기가 0 이하면 던진다', () => {
    assert.throws(() => chunkTokens(['a'], 0));
  });
});

describe('안심 메시지 푸시', () => {
  it('데이터 메시지만 만든다', () => {
    const payload = safetyMessagePayload(
      { senderUid: 'me', senderName: '민수', message: '나는 괜찮아', sharing: true },
      'g1',
      'a1',
      ['token-a'],
    );

    assert.deepEqual(payload.data, {
      [MessageKeys.type]: MessageTypes.safetyMessage,
      [MessageKeys.groupId]: 'g1',
      [MessageKeys.alertId]: 'a1',
      [MessageKeys.senderName]: '민수',
      [MessageKeys.message]: '나는 괜찮아',
      [MessageKeys.sharing]: 'true',
    });
  });

  it('모든 값이 문자열이다', () => {
    // FCM 데이터 메시지는 문자열만 담을 수 있다. boolean 을 그대로 넣으면 발송이 실패한다.
    const payload = safetyMessagePayload({ sharing: false }, 'g1', 'a1', ['t']);

    for (const value of Object.values(payload.data)) {
      assert.equal(typeof value, 'string');
    }
  });

  it('공유하지 않으면 sharing 이 false 문자열이다', () => {
    const payload = safetyMessagePayload({ sharing: false }, 'g1', 'a1', ['t']);

    assert.equal(payload.data[MessageKeys.sharing], 'false');
  });

  it('sharing 이 없으면 false 로 본다', () => {
    const payload = safetyMessagePayload({}, 'g1', 'a1', ['t']);

    assert.equal(payload.data[MessageKeys.sharing], 'false');
  });

  it('메시지가 비어 있으면 기본 문구를 넣는다', () => {
    // 아무 내용 없는 알림이 가는 것보다 기본 문구가 낫다.
    assert.equal(
      safetyMessagePayload({ message: '' }, 'g1', 'a1', ['t']).data[MessageKeys.message],
      DEFAULT_MESSAGE,
    );
    assert.equal(
      safetyMessagePayload({ message: '   ' }, 'g1', 'a1', ['t']).data[MessageKeys.message],
      DEFAULT_MESSAGE,
    );
    assert.equal(
      safetyMessagePayload({}, 'g1', 'a1', ['t']).data[MessageKeys.message],
      DEFAULT_MESSAGE,
    );
  });

  it('토큰을 그대로 들고 간다', () => {
    const payload = safetyMessagePayload({}, 'g1', 'a1', ['x', 'y']);

    assert.deepEqual(payload.tokens, ['x', 'y']);
  });
});

describe('공유 시작 푸시', () => {
  it('세션 ID 를 알림 ID 로 쓴다', () => {
    // 앱이 이 값으로 알림 ID 를 나눈다. 세션 ID 를 쓰면 같은 세션의 알림이 겹쳐 쓰인다.
    const payload = shareStartedPayload('지은', 'g1', 'session-1', ['t']);

    assert.equal(payload.data[MessageKeys.type], MessageTypes.shareStarted);
    assert.equal(payload.data[MessageKeys.alertId], 'session-1');
    assert.equal(payload.data[MessageKeys.senderName], '지은');
  });
});

describe('공유 시작 판단', () => {
  it('false 에서 true 로 바뀌면 시작이다', () => {
    assert.equal(isShareJustStarted({ active: false }, { active: true }), true);
  });

  it('sharing 이 아예 없다가 생기면 시작이다', () => {
    assert.equal(isShareJustStarted(undefined, { active: true }), true);
    assert.equal(isShareJustStarted(null, { active: true }), true);
  });

  it('공유 중이 아니면 시작이 아니다', () => {
    assert.equal(isShareJustStarted({ active: true }, { active: false }), false);
    assert.equal(isShareJustStarted(undefined, undefined), false);
    assert.equal(isShareJustStarted({ active: false }, { active: false }), false);
  });

  it('같은 세션에서 다른 값이 바뀌어도 시작이 아니다', () => {
    // 구성원 문서는 이름·일시중지·푸시 토큰 때문에도 바뀐다. 그때 알림을 보내면 안 된다.
    assert.equal(
      isShareJustStarted(
        { active: true, sessionId: 's1' },
        { active: true, sessionId: 's1' },
      ),
      false,
    );
  });

  it('세션이 바뀌면 새 시작으로 본다', () => {
    // 한 세션이 끝나고 바로 새 세션을 시작한 경우. active 가 false 를 거치지 않을 수 있다.
    assert.equal(
      isShareJustStarted(
        { active: true, sessionId: 's1' },
        { active: true, sessionId: 's2' },
      ),
      true,
    );
  });
});

describe('만료된 위치 지우기', () => {
  it('만료 시각이 지났으면 지운다', () => {
    assert.equal(shouldDeleteLocation({ expiresAt: NOW - 1 }, NOW), true);
  });

  it('만료 시각에 닿으면 지운다', () => {
    assert.equal(shouldDeleteLocation({ expiresAt: NOW }, NOW), true);
  });

  it('아직 안 지났으면 두 둔다', () => {
    assert.equal(shouldDeleteLocation({ expiresAt: NOW + 1 }, NOW), false);
  });

  it('만료 시각이 없으면 지운다', () => {
    // 예전 버전이 쓴 문서이거나 어긋난 문서다. 언제 지워야 할지 모르는 위치 문서를
    // 영원히 두는 것보다 지우는 쪽이 맞다.
    assert.equal(shouldDeleteLocation({}, NOW), true);
    assert.equal(shouldDeleteLocation({ expiresAt: null }, NOW), true);
    assert.equal(shouldDeleteLocation({ expiresAt: 'soon' }, NOW), true);
    assert.equal(shouldDeleteLocation({ expiresAt: Number.NaN }, NOW), true);
  });
});

describe('멈춘 공유 표시 끄기', () => {
  it('공유 중이 아니면 건드리지 않는다', () => {
    assert.equal(shouldClearSharing({ active: false, endsAt: NOW - 1 }, NOW), false);
    assert.equal(shouldClearSharing(undefined, NOW), false);
    assert.equal(shouldClearSharing(null, NOW), false);
  });

  it('끝 시각이 여유를 넘겨 지났으면 끈다', () => {
    const endsAt = NOW - SHARING_GRACE_MILLIS - 1;

    assert.equal(shouldClearSharing({ active: true, endsAt }, NOW), true);
  });

  it('여유 안쪽이면 두 둔다', () => {
    // 기기 시계와 서버 시계가 조금 다를 수 있다. 아직 공유 중인 사람의 표시를
    // 서버가 꺼 버리면 가족 위치 보기에서 사라진다.
    assert.equal(shouldClearSharing({ active: true, endsAt: NOW - 60_000 }, NOW), false);
  });

  it('아직 공유 중이면 두 둔다', () => {
    assert.equal(
      shouldClearSharing({ active: true, endsAt: NOW + 30 * 60_000 }, NOW),
      false,
    );
  });

  it('끝 시각을 모르면서 공유 중이면 끈다', () => {
    assert.equal(shouldClearSharing({ active: true }, NOW), true);
    assert.equal(shouldClearSharing({ active: true, endsAt: 'later' }, NOW), true);
  });
});

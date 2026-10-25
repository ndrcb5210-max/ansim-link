# functions — 안심연결 Cloud Functions

그룹 푸시 발송과 세션 정리. **이 저장소에서는 배포하지 않는다.**

## 왜 서버가 필요한가

FCM 발송에는 서버 자격 증명(서비스 계정 키)이 필요하다. 그 키를 앱에 넣으면 APK 를 뜯어
꺼낸 누구나 아무에게나 푸시를 보낼 수 있다. 그래서 앱은 Firestore 문서만 만들고
(`groups/{gid}/alerts/{id}`) 서버가 그 문서를 보고 보낸다.

앱에는 발송 코드가 **없다**. `ShareRepository` 에 FCM 을 보내는 함수를 두지 않은 것이
그 때문이다.

## 배포에 Blaze 요금제가 필요하다

Cloud Functions(2세대)는 Cloud Run·Artifact Registry·Cloud Build 를 쓰므로
**Blaze(종량제) 요금제**여야 배포된다. 무료 Spark 요금제에서는 `firebase deploy` 가
`Your project must be on the Blaze (pay-as-you-go) plan to complete this command` 로
멈춘다.

Blaze 로 올려도 무료 할당량이 있어서(월 200만 호출 등) 가족 몇 명이 쓰는 규모에서는
요금이 거의 들지 않는다. 다만 **한도를 넘으면 과금되므로** 예산 알림을 먼저 걸어 두는
편이 좋다(Google Cloud Console > 결제 > 예산 및 알림).

이 저장소는 **코드만 둔다.** 배포하지 않아도 앱은 돌아간다. 다만 서버가 없으면
- 안심 메시지 문서는 만들어지지만 **푸시가 가지 않는다**(받는 쪽이 앱을 열어야 보인다),
- 공유가 끝난 위치가 **24시간 뒤에 지워지지 않는다**(원칙 4 를 지키려면 배포가 필요하다).

## 함수

| 함수 | 종류 | 하는 일 |
| --- | --- | --- |
| `onAlertCreated` | Firestore 문서 생성 | `groups/{gid}/alerts/{id}` 가 만들어지면 그룹 구성원에게 FCM 발송(보낸 사람 제외) |
| `onShareStarted` | Firestore 문서 변경 | `members/{uid}.sharing.active` 가 false→true 로 바뀌면 다른 구성원에게 알림 |
| `cleanupExpiredLocations` | 예약(매시 7분) | `expiresAt` 이 지난 위치 문서 삭제 + 멈춘 `sharing.active` 끄기 |
| `cleanupOrphanLocations` | 예약(매일 04:23) | `expiresAt` 이 없는 위치 문서 정리 |

모두 `asia-northeast3`(서울)에 둔다. Firestore 도 같은 지역에 두면 지연이 줄고
리전 간 트래픽 비용이 없다.

### 왜 데이터 메시지만 보내나

`notification` 블록을 쓰지 않고 `data` 만 보낸다. `notification` 을 쓰면 앱이
백그라운드일 때 시스템이 알림을 직접 띄워 버려서

- `[위치 보기]` 버튼을 붙일 수 없고,
- 알림 ID 를 우리가 정할 수 없어 같은 메시지가 두 줄로 쌓인다.

대가: 데이터 메시지는 앱이 강제 종료된 상태에서는 오지 않을 수 있고 Doze 중에 늦게 올 수
있다. 그래서 `android.priority = 'high'` 로 보낸다.

### 왜 `cleanupOrphanLocations` 가 따로 있나

`expiresAt <= now` 질의는 **그 필드가 없는 문서를 아예 가져오지 못한다**(Firestore 는
필드가 없는 문서를 범위 질의에서 뺀다). 예전 버전이 쓴 문서나 어긋난 문서가 영원히 남을
수 있어서 하루 한 번 전체를 훑는 함수를 따로 두었다. 보통은 한 건도 없다.

## 단위 테스트

```bash
cd functions
npm install
npm test
```

**에뮬레이터가 필요 없다.** 판단이 들어가는 부분(누구에게 보낼지·무엇을 보낼지·무엇을
지울지)을 `src/messages.ts` 의 순수 함수로 떼어 두었고, 테스트는 그 함수들만 확인한다.
`src/index.ts` 는 Firestore 를 읽어 그 함수에 넘기고 결과를 FCM·Firestore 에 쓰는
배선뿐이다.

`firebase-functions-test` 와 에뮬레이터를 쓰는 통합 테스트는 넣지 않았다. 느리고 CI 에서
깨지기 쉬운데, 그것으로 더 확인할 수 있는 것은 '배선이 맞는지'뿐이다. 배선은 실제 배포
뒤에 처음 검증된다(아래 '확인하지 못한 것').

## 빌드와 배포 (하지 않았다)

```bash
cd functions
npm install
npm run build          # tsc → lib/

# 아래는 Blaze 요금제가 있어야 된다. 이 저장소에서는 실행하지 않았다.
firebase login
firebase use <프로젝트-ID>
firebase deploy --only functions

# 함수 하나만:
firebase deploy --only functions:onAlertCreated
```

예약 함수(`onSchedule`)는 첫 배포 때 Cloud Scheduler 작업을 같이 만든다. Scheduler 는
작업 3개까지 무료다. 지금 예약 함수가 2개라 그 안에 들어간다.

### 배포 전에 확인할 것

1. **Firestore 지역**이 `asia-northeast3` 인지. 다르면 `src/index.ts` 의 `REGION` 을
   맞춰야 한다(지역이 다르면 동작은 하지만 지연과 비용이 늘어난다).
2. **서비스 계정 키를 저장소에 넣지 않는다.** 배포는 `firebase login` 으로 한 사용자
   자격으로 하고, CI 에서 배포하려면 GitHub Actions 비밀값에 둔다
   (CLAUDE.md 원칙 6).
3. 예산 알림을 걸어 두었는지.

## 확인하지 못한 것 (TODO)

- **배포해 본 적이 없다.** 이 환경에는 Blaze 프로젝트가 없다. 함수 선언이 실제로
  트리거되는지, FCM 발송이 되는지는 배포 뒤에 처음 검증된다. 컴파일(`npm run build`)과
  순수 함수 단위 테스트까지만 확인했다.
- **컬렉션 그룹 질의 색인.** `collectionGroup('members').where('sharing.active', '==', true)`
  와 `collectionGroup('locations').where('expiresAt', '<=', ...)` 는 한 필드 질의라
  자동 색인으로 될 것으로 보지만, 실제로 색인 오류가 나는지 배포해서 확인해야 한다.
  오류가 나면 `firestore.indexes.json` 을 추가해야 한다.
- **`removeInvalidTokens` 의 uid 추정.** 사본(`members/{uid}.pushToken`)의 문서 ID 가
  uid 라는 점을 이용해 원본(`users/{uid}/pushTokens/{token}`)을 지운다. 구조상 맞지만
  실제 데이터로 확인하지 못했다.

# functions — 안심연결 Cloud Functions

그룹 푸시 발송, 세션 정리, 구독 검증. **이 저장소에서는 배포하지 않는다.**

## 왜 서버가 필요한가

두 가지 일이 서버 자격 증명을 필요로 한다.

1. **FCM 발송.** 그 키를 앱에 넣으면 APK 를 뜯어 꺼낸 누구나 아무에게나 푸시를 보낼 수
   있다. 그래서 앱은 Firestore 문서만 만들고(`groups/{gid}/alerts/{id}`) 서버가 그
   문서를 보고 보낸다.
2. **구독 검증.** Play Developer API 는 서비스 계정 자격 증명이 필요하다. 그 키를 앱에
   넣으면 누구나 남의 구독을 조회할 수 있다. 그래서 앱은 구매 토큰만 넘기고
   (`verifySubscription`) 서버가 Play 에 물어본 결과를 `users/{uid}.entitlement` 에 쓴다.

앱에는 발송 코드도 검증 코드도 **없다**. `ShareRepository` 에 FCM 발송 함수를,
`EntitlementRepository` 에 쓰기 함수를 두지 않은 것이 그 때문이다.

## 앱이 서버 값을 믿는 자리는 한 곳뿐이다

프리미엄을 여는 근거는 `users/{uid}.entitlement` **하나**다. `verifySubscription` 의
응답으로 열지 않는다(중간에서 응답을 바꿔치기하면 프리미엄이 열린다). 그리고 보안
규칙이 그 필드의 클라이언트 쓰기를 막는다 — 앱 코드에 그 쓰기가 없다는 것만으로는
모자라다.

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
| `verifySubscription` | callable | 구매 토큰을 Play 에 확인하고 `users/{uid}.entitlement` 를 쓴다 (5단계) |
| `onPlayNotification` | Pub/Sub | 실시간 개발자 알림(RTDN)을 받아 구독 상태를 다시 확인한다 (5단계) |

모두 `asia-northeast3`(서울)에 둔다. Firestore 도 같은 지역에 두면 지연이 줄고
리전 간 트래픽 비용이 없다. 앱의 `AppContainer.FUNCTIONS_REGION` 도 같은 값이어야
한다 — 다르면 앱이 함수를 못 찾는다.

### 왜 데이터 메시지만 보내나

`notification` 블록을 쓰지 않고 `data` 만 보낸다. `notification` 을 쓰면 앱이
백그라운드일 때 시스템이 알림을 직접 띄워 버려서

- `[위치 보기]` 버튼을 붙일 수 없고,
- 알림 ID 를 우리가 정할 수 없어 같은 메시지가 두 줄로 쌓인다.

대가: 데이터 메시지는 앱이 강제 종료된 상태에서는 오지 않을 수 있고 Doze 중에 늦게 올 수
있다. 그래서 `android.priority = 'high'` 로 보낸다.

### 구독 검증(`verifySubscription`)이 하는 일과 하지 않는 일

**하는 일**

1. 로그인 확인. 로그인하지 않으면 `unauthenticated`.
2. `purchases.subscriptionsv2.get` 으로 지금 구독 상태를 Play 에 물어본다.
3. 이 구매가 **이 계정의 것인지** 확인한다. 앱이 결제할 때 `obfuscatedAccountId` 로
   uid 의 SHA-256 을 넘기므로 같은 계산으로 맞춰 본다. Play 가 그 값을 돌려주지 않으면
   통과시킨다(예전 구매·묶음 구독에는 없다 — 없는 것을 '남의 구매'로 보면 정당한
   구독자를 막는다). 값이 있는데 다르면 `permission-denied`.
4. `users/{uid}.entitlement` 를 쓴다(merge, 그 필드만).
5. 구매 토큰 → uid 매핑을 남긴다(`purchaseTokens/{sha256}`). RTDN 에는 uid 가 없다.

**하지 않는 일**

- **확인(acknowledge)을 하지 않는다.** 앱이 이 함수의 성공을 받은 뒤 Billing
  라이브러리로 한다. 양쪽에서 하면 '서버는 확인했는데 앱은 모르는' 상태가 생긴다.
  어느 쪽이든 3일 안에만 하면 되므로 한쪽에서만 한다.
- 금액·기간을 응답에 담지 않는다. 앱은 Firestore 에서 읽는다.

### 왜 알림(RTDN) 종류만 보고 고치지 않나

알림은 **순서가 보장되지 않는다.** 늦게 도착한 '갱신됨'으로 이미 해지된 구독을
되살리면 안 된다. 그래서 거의 모든 알림을 '지금 상태를 Play 에 다시 물어본다'로
처리한다(`entitlement.ts` 의 `notificationAction`).

예외는 회수·환불(`SUBSCRIPTION_REVOKED`, `voidedPurchaseNotification`)이다. 이것은
즉시 막는다 — 다시 물어보는 동안 열려 있는 틈을 두지 않는다.

재시도를 끈 이유(`retry: false`): 하는 일이 '다시 물어보기'라 멱등이고, 실패한 알림을
끝없이 다시 받으면 Developer API 쿼터만 쓴다. 놓친 상태는 사용자가 앱을 열 때
`verifySubscription` 이 바로잡는다.

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
4. 구독 검증 설정(아래)이 끝났는지.

## 구독 검증 설정 (5단계)

### 1. 앱 패키지 이름

```bash
firebase functions:config:unset   # 1세대 config 는 쓰지 않는다
firebase deploy --only functions  # 아래 환경 변수를 .env 로 넘긴다
```

2세대 함수는 `defineString` 으로 환경 변수를 읽는다. `functions/.env`
(**저장소에 넣지 않는다** — `.gitignore` 에 있다)에 적는다.

```
ANDROID_PACKAGE_NAME=io.github.ndrcb5210max.ansimlink
```

비우면 구독 검증이 `internal` 오류를 내고, 알림의 패키지 검사를 건너뛴다.

### 2. Play Developer API 자격 증명 — 키 없이 하는 쪽을 권한다

**방법 A (권장, 키 파일이 없다).**

1. Play Console > 설정 > API 액세스에서 Google Cloud 프로젝트를 Firebase 프로젝트와
   연결한다.
2. Cloud Functions 의 기본 서비스 계정 이메일을 확인한다
   (`<프로젝트-ID>@appspot.gserviceaccount.com` 또는 Cloud Run 의 컴퓨팅 서비스 계정).
3. Play Console > 사용자 및 권한 > 사용자 초대로 그 이메일을 초대하고
   **'재무 데이터, 주문, 설문조사 응답 보기'** 권한을 준다.
4. `PLAY_SERVICE_ACCOUNT_JSON` 비밀값을 **만들지 않는다.** 함수가 기본 서비스
   계정(ADC)으로 API 를 부른다.

키 파일이 아예 없으므로 유출될 것도 없다. CLAUDE.md 원칙 6과 가장 잘 맞는다.

**방법 B (방법 A 가 막혔을 때만).**

Google Cloud 콘솔에서 서비스 계정을 만들고 JSON 키를 받아 Play Console 에 초대한 뒤,
키 전문을 Secret Manager 에 넣는다.

```bash
firebase functions:secrets:set PLAY_SERVICE_ACCOUNT_JSON
# 붙여넣기 창이 열린다. 파일 경로가 아니라 JSON 전문을 넣는다.
```

**그 JSON 파일을 저장소에 두지 않는다.** `.gitignore` 에 `service-account*.json` 이
들어 있지만, 다른 이름으로 두는 실수를 막아 주지는 못한다.

### 3. 실시간 개발자 알림(RTDN)

1. Google Cloud 콘솔 > Pub/Sub 에서 주제를 만든다. 이름은 `src/index.ts` 의
   `PLAY_NOTIFICATION_TOPIC`(`play-billing-notifications`)과 같아야 한다.
2. 그 주제에 `google-play-developer-notifications@system.gserviceaccount.com` 에게
   **Pub/Sub 발행자** 권한을 준다(Play 가 이 계정으로 보낸다).
3. Play Console > 수익 창출 설정 > 실시간 개발자 알림에 주제 전체 이름을 적는다
   (`projects/<프로젝트-ID>/topics/play-billing-notifications`).
4. 같은 화면의 '테스트 알림 보내기'로 함수가 트리거되는지 확인한다. 테스트 알림에는
   구매 토큰이 없어서 함수는 '처리하지 않는다'를 기록하고 끝난다 — 그 로그가 보이면
   배선이 맞는 것이다.

### 4. 구독 상품 만들기

Play Console > 수익 창출 > 구독에서 상품을 만든다.

- 상품 ID: `premium_monthly` (앱의 `SubscriptionCatalog.PREMIUM_MONTHLY` 와 **글자
  하나까지 같아야 한다.** 다르면 앱이 가격을 못 받아 결제 버튼이 꺼진다.)
- 기준 요금제: 월 자동 갱신.
- 무료 체험을 둘 거면 그 기준 요금제의 혜택(offer)으로 만든다. 앱은 받을 수 있는
  혜택 중 가장 유리한 것을 골라 보여 준다(`SubscriptionOffers.choose`).

상품을 **활성화**해야 `queryProductDetailsAsync` 가 돌려준다. 초안 상태면 빈 목록이
오고 앱은 '구독 상품을 찾을 수 없습니다'를 보여 준다.

### 5. 실제 결제 시험

자세한 절차는 `docs/devlog/05-billing.md` 의 '실제 결제 시험에 필요한 절차'에 적었다.
요점만:

- Play Console > 설정 > **라이선스 테스트**에 시험할 계정을 넣는다. 실제 결제 없이
  결제 흐름을 돌릴 수 있고, **라이선스 테스터는 'Play 에서 설치했는가' 검사를
  건너뛴다** — 디버그 서명으로 사이드로드한 빌드로도 된다(공식 문서).
  패키지 이름이 Console 의 앱과 같기만 하면 된다.
- 라이선스 테스터가 **아닌** 계정에서는 Play 에서 설치한 앱이어야 한다. 그때는 내부
  테스트 트랙에 서명된 AAB 를 올리고 테스터 링크로 설치한다.
- 테스트 구독은 주기가 짧아진다(월 구독 → 5분, 무료 체험 → 3분, 유예 기간 → 5분,
  보류 → 10분, 최대 6번 갱신 뒤 종료). 해지·만료·보류까지 하루 안에 다 볼 수 있다.

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

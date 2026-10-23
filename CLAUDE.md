# CLAUDE.md — 안심연결 (Ansim Link)

이 파일은 Claude Code가 이 저장소에서 일할 때 따르는 제품 명세와 작업 규칙이다. 사람(블로그 독자)도 읽는다.

## 1. 제품 한 줄

휴대폰이 **재난문자**(위급재난문자·긴급재난문자·안전안내문자)를 받으면, 미리 만든 **안심 그룹**에서
1. 위치 공유에 **동의한 구성원들의 위치를 정해진 시간 동안 실시간으로 공유**하고,
2. 미리 적어 둔 **안심 메시지를 그룹 구성원 모두에게 보낸다.**

문자 종류(3단계)와 재난 유형(지진, 호우 등)마다 발동 여부를 따로 정할 수 있다.

## 2. 반드시 지킬 원칙 (어기면 안 됨)

1. **동의가 먼저다.** 구성원은 초대 코드로 가입한 뒤 ‘위치 공유 동의’ 화면에서 직접 동의해야 그룹에 들어온다. 동의는 언제든 철회·일시중지할 수 있고 즉시 반영된다. 동의하지 않은 사람의 위치는 어떤 경우에도 수집·전송하지 않는다.
2. **숨기지 않는다.** 위치 공유가 진행되는 동안에는 항상 상시 알림(포그라운드 서비스 알림)과 앱 안 배너를 보여 준다. 공유가 시작될 때마다 당사자에게 알린다.
3. **시간 제한.** 공유는 발동 후 기본 60분(설정 30~180분) 뒤 자동 종료한다. 알림과 앱 화면에 ‘지금 중지’ 버튼을 둔다.
4. **최소 수집.** 위치는 공유 세션 동안만 저장한다. 세션이 끝나면 구성원별 마지막 위치 1건만 남기고, 그것도 24시간 뒤 지운다. 재난문자 알림 외의 다른 알림 내용은 읽어도 저장하지 않는다.
5. **SMS를 쓰지 않는다.** 메시지는 앱 푸시(FCM)로 그룹 구성원에게만 간다. SEND_SMS/READ_SMS 권한을 추가하지 않는다.
6. **비밀 정보는 커밋하지 않는다.** google-services.json(실제 값), 키스토어, 서비스 계정 키, API 키는 저장소에 넣지 않는다. CI는 가짜 google-services.json으로 빌드한다.

## 3. 기술 스택과 구조

- Android: Kotlin, Jetpack Compose(Material 3), Navigation Compose, Coroutines/Flow, DataStore.
  - `minSdk 26`, `targetSdk 36`, `compileSdk 36`. Gradle Kotlin DSL + version catalog(`gradle/libs.versions.toml`).
  - 패키지: `io.github.ndrcb5210max.ansimlink`
  - DI는 단순하게 수동 주입(AppContainer)으로 한다.
- Firebase: Authentication(이메일/비밀번호, Google 로그인은 Credential Manager), Cloud Firestore, Cloud Messaging.
- 서버: `functions/`에 Cloud Functions(TypeScript)를 둔다(그룹 푸시 발송, 구독 검증). 배포는 Blaze 요금제가 필요하므로 코드만 두고 배포 절차는 문서로 안내한다.
- 결제: Google Play Billing Library 8.x, 구독 상품 `premium_monthly`.
  - 무료: 그룹 1개, 구성원 3명, 공유 최대 60분, 공통 안심 메시지 1개.
  - 프리미엄: 그룹 3개, 구성원 10명, 공유 최대 180분, 재난 유형별 메시지.
  - 구독 상태는 서버(Cloud Functions + Play Developer API)에서 검증한 값을 Firestore에 두고 앱은 그것을 읽는다. 앱 단독 판단으로 기능을 열지 않는다.
- 재난문자 감지: 일반 앱은 셀 브로드캐스트를 직접 받을 수 없으므로(시스템 권한), **알림 접근 권한(NotificationListenerService)** 으로 휴대폰의 재난문자 앱이 띄운 알림을 읽어 판단한다.
  - 대상 패키지 목록은 설정 파일로 관리한다(예: `com.android.cellbroadcastreceiver`, `com.google.android.cellbroadcastreceiver`, 삼성 기기 패키지).
  - 문자 종류: 알림 제목·채널명에서 ‘위급재난’, ‘긴급재난’, ‘안전안내’를 공백 제거 후 찾는다.
  - 재난 유형: 본문 키워드로 분류한다(지진, 지진해일, 호우, 홍수, 태풍, 산사태, 산불, 대설, 한파, 폭염, 민방공, 기타). 분류기는 순수 Kotlin으로 만들고 단위 테스트를 단다.
  - 같은 문자가 여러 번 와도 10분 안에는 한 번만 발동한다.
  - 설정 화면에 ‘테스트 발동’ 버튼을 둔다(실제 재난문자 없이 흐름 점검).
- 위치: Fused Location Provider, 포그라운드 서비스 타입 `location`, 30초 간격, 발동 시에만 시작.
- 지도: API 키가 필요 없는 방식으로 시작한다(구성원별 마지막 위치·시각·거리 목록 + 외부 지도 앱 열기).
- 테스트: 분류기·규칙 엔진은 JUnit, 화면은 Roborazzi 스크린샷 테스트(블로그용 이미지도 여기서 만든다).
- CI: GitHub Actions에서 `./gradlew lint testDebugUnitTest assembleDebug`, APK를 아티팩트로 올린다.

## 4. 단계 계획 (블로그 연재와 같은 순서)

1. 뼈대: 프로젝트 생성, 화면 3개(홈·그룹·설정) 빈 틀, 내비게이션, CI, Roborazzi.
2. 회원가입·로그인·계정 삭제(앱 안 + 웹 안내 URL), 그룹 만들기·초대 코드·동의 화면, Firestore 스키마와 보안 규칙.
3. 재난문자 감지: NotificationListenerService, 분류기, 규칙(문자 종류 × 재난 유형) 설정 화면, 발동 기록.
4. 위치 공유와 메시지: 포그라운드 서비스, Firestore 실시간 위치, 안심 메시지 편집, `functions/` 푸시 발송, FCM 수신 처리.
5. 결제: Play Billing 구독, 결제 화면(paywall), 무료/프리미엄 한도 적용, 서버 검증 함수.
6. 출시 준비: 개인정보 처리방침 초안, 데이터 보안 항목 정리, 권한 선언 문구, 릴리스 빌드 서명 설정(환경 변수), 출시 안내 문서.

## 5. 작업 규칙

- 단계마다 `feature/<단계>` 브랜치에서 작업하고, 끝나면 `./gradlew testDebugUnitTest assembleDebug`가 통과해야 한다.
- 커밋 메시지는 한국어 한 줄 요약 + 필요하면 본문. 예: `feat: 재난문자 분류기와 단위 테스트 추가`.
- UI 문구는 한국어, 쉬운 말. 권한을 요청하기 전에는 왜 필요한지 먼저 설명하는 화면(prominent disclosure)을 띄운다.
- 사용자에게 보이는 문구에 과장 금지: “재난문자를 놓치지 않습니다” 같은 보장 표현을 쓰지 않는다. 휴대폰·통신사·설정에 따라 감지가 안 될 수 있음을 설명 화면에 적는다.
- 모르는 API나 버전은 추측하지 말고 공식 문서를 확인한 뒤 쓴다. 확인하지 못했으면 TODO로 남기고 이유를 적는다.
- 각 단계가 끝나면 `docs/devlog/NN-<단계>.md`에 한 일, 막힌 점, 남은 일을 짧게 적는다.

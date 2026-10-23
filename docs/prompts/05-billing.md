CLAUDE.md를 먼저 읽고 **5단계(구독 결제)** 를 진행해 줘. 지금 브랜치는 `feature/05-billing`이다. 앞 단계 devlog를 먼저 확인해.

배경(이미 확인한 사실)
- 앱 안 디지털 기능 구독은 Google Play 결제를 써야 한다(한국은 대체결제를 함께 둘 수 있지만 이 앱은 Play 결제만 쓴다).
- 2026년 8월 31일부터 새 앱은 Play Billing Library 8 이상이어야 한다. 9.0.0이 2026년 5월에 나왔다.
- 구독 화면에는 가격, 결제 주기, 자동 갱신, 무료 체험이 있으면 전환 시점·금액, 해지 방법을 분명히 보여야 하고, 앱 안에 구독 관리(해지) 경로가 있어야 한다.
- 실제 결제 시험은 Play Console에 앱과 상품을 등록하고 라이선스 테스터를 지정해야 할 수 있다. 이 연재에서는 Play Console 계정이 아직 없다.

할 일
1. Play Billing Library를 현재 안정 버전(8 이상, 9 권장 여부 확인)으로 넣고 `BillingRepository`를 만든다: 연결, 상품 조회(`premium_monthly`), 구매 흐름, 구매 업데이트 처리, 확인(acknowledge)은 서버 검증 뒤.
2. **서버 검증**(functions/): `verifySubscription` callable 함수 — 구매 토큰을 받아 Google Play Developer API(purchases.subscriptionsv2.get)로 확인하고 `users/{uid}.entitlement`(plan, expiryTime, autoRenewing, state)를 쓴다. 실시간 개발자 알림(RTDN, Pub/Sub)을 받는 함수 골격도 둔다. 서비스 계정은 환경 설정으로만 받는다. 배포는 하지 않는다.
3. 앱은 `users/{uid}.entitlement`만 보고 프리미엄 한도(그룹 3개, 구성원 10명, 공유 180분, 유형별 메시지)를 연다. 앱이 단독으로 프리미엄을 켜지 않는다.
4. **결제 화면(paywall)**: 무료/프리미엄 비교표, 가격(Play에서 받은 formattedPrice), “매월 자동 갱신, 언제든 Google Play에서 해지” 문구, [구독 관리] 링크(`https://play.google.com/store/account/subscriptions?sku=...&package=...`), 이용약관·처리방침 링크. 가격을 못 받으면 결제 버튼을 끄고 이유를 보여 준다.
5. 설정 > 구독: 현재 상태, 갱신일, [구독 관리]. 계정 삭제 화면의 구독 안내와 연결.
6. 디버그 빌드 전용 ‘가짜 결제 저장소’(FakeBillingRepository)로 화면 흐름과 한도 적용을 시험할 수 있게 한다(릴리스에는 포함되지 않게).
7. 테스트: 한도 판정, 엔타이틀먼트 만료 처리, 가격 미수신 시 버튼 비활성 등 단위 테스트. Roborazzi `docs/screenshots/05/`. `./gradlew lint testDebugUnitTest assembleDebug` 통과, `docs/devlog/05-billing.md`(실제 결제 시험에 필요한 Play Console 절차를 정리), 의미 단위 커밋(푸시는 하지 마라).

하지 말 것: 결제 우회, 클라이언트 단독 권한 부여, 서비스 계정 키 커밋.

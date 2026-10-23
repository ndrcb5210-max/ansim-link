CLAUDE.md를 먼저 읽고 **2단계(회원가입·동의·계정 삭제, 그룹과 초대)** 를 진행해 줘. 지금 브랜치는 `feature/02-auth-consent`다. 1단계 결과(docs/devlog/01-scaffold.md)를 먼저 확인해.

할 일
1. Firebase 연동: Firebase BoM, Authentication, Cloud Firestore를 현재 안정 버전으로 확인해서 넣는다(compileSdk 36과 호환되는지 1단계처럼 확인).
   - 실제 `google-services.json`은 저장소에 없다. CI와 로컬 테스트용으로 **가짜 google-services.json을 만드는 스크립트**(`scripts/fake-google-services.sh`)를 두고, 워크플로에서 실제 파일이 없을 때만 쓴다.
   - 앱은 Firebase 설정이 가짜이거나 초기화에 실패해도 죽지 않고 ‘서버 설정이 필요합니다’ 안내를 보여 준다.
2. 회원가입·로그인
   - 이메일·비밀번호 가입(인증 메일 발송)과 로그인, 비밀번호 재설정.
   - Google 로그인: Credential Manager + Sign in with Google(androidx.credentials, googleid). Web client ID는 `local.properties`/환경 변수로 받고 없으면 버튼을 숨긴다.
   - 가입 전에 **‘만 14세 이상입니다’ 확인(필수)** 과 **이용약관, 개인정보 처리방침, 위치정보 이용약관** 동의를 각각 체크하게 한다. ‘전체 동의’ 버튼은 있어도 되지만 항목별 체크가 보여야 한다. 뒤로 가기는 동의가 아니다.
   - 동의 항목·시각·약관 버전을 `users/{uid}.consents`에 저장한다.
   - 약관 초안 3개를 `docs/legal/`에 마크다운으로 만든다(위치정보법 제18조·제19조 항목: 사업자 상호·연락처 자리, 권리와 행사 방법, 서비스 내용, 제공받는 자, 확인자료 보유 기간 등). 앱은 이 문서를 화면에서 보여 준다(앱 안 리소스로 복사).
3. 계정 삭제(구글 플레이 계정 삭제 정책)
   - 설정 > 계정 > 계정 삭제: 재인증 → Firestore의 내 데이터(사용자 문서, 그룹 멤버십, 위치, 푸시 토큰) 삭제 → Auth 계정 삭제. 내가 만든 그룹은 남은 구성원이 없으면 삭제, 있으면 소유권을 넘기거나 그룹을 해산하는 선택지를 준다.
   - 구독 중이면 삭제 전에 Google Play 구독을 해지해야 한다는 안내와 구독 관리 링크를 보여 준다(결제 코드는 아직 없으니 안내만).
   - 앱 밖 삭제 요청용 웹 문서 초안 `docs/account-deletion.md`를 만든다(나중에 GitHub Pages로 공개할 것).
4. 안심 그룹과 초대
   - 그룹 만들기(이름), 초대 코드(6자리, 24시간 유효) 만들기, 코드로 참여.
   - 참여할 때 **위치 제공 동의 화면**: “이 그룹 구성원(이름 목록)에게, 재난문자 발동 시, 최대 N분 동안 내 위치가 제공됩니다”를 보여 주고 체크해야 참여된다(위치정보법 제19조②: 제공받는 자·목적 고지).
   - 그룹 화면에 구성원 목록과 각자의 공유 상태(동의함/일시중지/동의 철회)를 모두에게 보여 준다(서로 보이게).
   - ‘내 공유 상태’에서 일시중지와 동의 철회를 한 번에 할 수 있게 한다(위치정보법 제24조). 철회하면 그룹에서 나가며 관련 데이터는 지운다.
   - 무료 한도(그룹 1개, 구성원 3명)를 상수로 두고 넘으면 안내한다(결제는 6단계).
5. Firestore 구조와 보안 규칙
   - `users/{uid}`, `groups/{gid}`(name, ownerUid, memberUids, createdAt), `groups/{gid}/members/{uid}`(displayName, consent{location, at, version}, paused), `groups/{gid}/invites/{code}`(expiresAt).
   - `firestore.rules`: 그룹 구성원만 그룹·구성원 문서를 읽고, 자기 구성원 문서만 쓰고, 유효한 초대 코드로만 참여한다.
   - 가능하면 `@firebase/rules-unit-testing`과 Firestore 에뮬레이터로 규칙 테스트를 `tests/rules/`에 만든다. 에뮬레이터 실행이 이 PC에서 안 되면 테스트 코드만 두고 이유를 devlog에 적는다.
6. 화면: 로그인/가입, 동의, 그룹(목록·만들기·참여·구성원·내 공유 상태), 설정의 계정 삭제. Roborazzi 스크린샷을 `docs/screenshots/02/`에 만든다.
7. 테스트: 필수 동의가 빠지면 가입 버튼이 꺼지는지, 초대 코드 만료 판정, 무료 한도 판정 등 단위 테스트.
8. `./gradlew lint testDebugUnitTest assembleDebug` 통과, `docs/devlog/02-auth-consent.md` 작성, 의미 단위로 커밋(푸시는 하지 마라).

하지 말 것: 위치 수집, 재난문자 감지, 결제 코드를 미리 넣지 마라. 실제 Firebase 키나 서비스 계정 파일을 만들거나 커밋하지 마라.

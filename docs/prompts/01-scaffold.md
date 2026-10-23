CLAUDE.md를 먼저 읽고, 4장 단계 계획의 **1단계(뼈대)** 를 진행해 줘. 지금 브랜치는 `feature/01-scaffold`다.

할 일
1. Android Studio 없이 명령줄 Gradle로 빌드되는 Kotlin + Jetpack Compose 앱 프로젝트를 만든다.
   - `settings.gradle.kts`, 루트·`app` 모듈 `build.gradle.kts`, `gradle/libs.versions.toml`(버전 카탈로그), `gradle.properties`.
   - Gradle Wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`)까지 갖춘다. 이 PC에는 gradle 명령이 없으니, 필요하면 Gradle 배포판을 내려받아 `gradle wrapper`로 만들고 내려받은 배포판은 저장소에 넣지 마라.
   - AGP·Kotlin·Compose BOM 등은 `compileSdk 36`/`targetSdk 36`을 지원하는 **현재 안정 버전**을 공식 문서나 Maven 저장소에서 확인해서 쓴다. 추측하지 마라.
2. 화면 3개(홈, 그룹, 설정)를 하단 탭 내비게이션으로 연결한 빈 틀을 만든다. 문구는 한국어.
   - 홈: 앱 이름, ‘재난문자 감지 상태’ 카드(아직 꺼짐), ‘위치 공유 중이 아님’ 상태 표시.
   - 그룹: ‘아직 그룹이 없습니다’ 빈 상태와 ‘그룹 만들기’ 버튼(동작은 다음 단계).
   - 설정: 항목 자리만(재난문자 감지 권한, 발동 규칙, 안심 메시지, 구독, 계정).
3. Roborazzi로 세 화면의 스크린샷 테스트를 만든다. 결과 PNG는 `docs/screenshots/01/`에 저장되게 설정한다(블로그에 쓸 것).
4. GitHub Actions 워크플로 `.github/workflows/android.yml`: push와 pull_request에서 JDK 21 설정 → `./gradlew lint testDebugUnitTest assembleDebug` → debug APK를 아티팩트로 업로드. Firebase는 아직 쓰지 않는다.
5. `.gitattributes`로 줄바꿈을 정리한다(`gradlew`와 `*.sh`는 LF, `*.bat`는 CRLF).
6. 로컬에서 `./gradlew testDebugUnitTest assembleDebug`를 실행해 통과시킨다. JAVA_HOME과 ANDROID_HOME은 이미 잡혀 있다.
7. `docs/devlog/01-scaffold.md`에 한 일, 고른 버전과 그 근거(확인한 문서), 막힌 점, 남은 일을 적는다.
8. 변경을 의미 있는 단위로 나눠 커밋한다(푸시는 하지 마라).

하지 말 것: Firebase·결제·위치 코드를 미리 넣지 마라. 비밀 값이나 키스토어를 만들지 마라.

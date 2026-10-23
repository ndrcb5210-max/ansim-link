# 1단계 — 뼈대

작업일: 2026-10-24 · 브랜치: `feature/01-scaffold`

## 한 일

- Android Studio 없이 명령줄 Gradle만으로 빌드되는 Kotlin + Jetpack Compose 앱을 만들었다.
  - `settings.gradle.kts`, 루트·`app` 모듈 `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`.
  - 이 PC에 `gradle` 명령이 없어서, Gradle 9.5.1 배포판을 내려받아 SHA-256을 맞춰 본 뒤
    그것으로 `gradle wrapper`를 돌려 래퍼를 만들고 배포판은 지웠다(저장소에 넣지 않았다).
  - `gradle-wrapper.properties`에 `distributionSha256Sum`을 넣어 래퍼가 받은 배포판을 검증하게 했다.
- 홈·그룹·설정 화면 세 개를 Material 3 `NavigationBar` 하단 탭으로 연결했다. 문구는 모두 한국어이고
  문자열은 `strings.xml`에 둔다.
  - 홈: 앱 이름, ‘재난문자 감지 — 꺼짐’ 카드, ‘위치 공유 — 공유 중이 아님’ 카드.
  - 그룹: ‘아직 그룹이 없습니다’ 빈 상태 + ‘그룹 만들기’ 버튼(동작은 2단계).
  - 설정: 재난문자 감지 권한 / 발동 규칙 / 안심 메시지 / 구독 / 계정 자리만.
- Roborazzi 스크린샷 테스트 3개를 만들고 결과 PNG가 `docs/screenshots/01/`에 저장되게 했다.
- GitHub Actions 워크플로(`.github/workflows/android.yml`)를 추가했다. JDK 21 → `lint testDebugUnitTest assembleDebug`
  → debug APK·보고서·스크린샷 아티팩트 업로드.
- `.gitattributes`로 줄바꿈을 정리했다(`gradlew`·`*.sh`는 LF, `*.bat`·`*.cmd`는 CRLF, 바이너리는 그대로).
- 로컬에서 `./gradlew lint testDebugUnitTest assembleDebug` 통과를 확인했다.

Firebase·결제·위치 코드는 넣지 않았다. 매니페스트에 권한도 하나도 선언하지 않았다.
비밀 값과 키스토어도 만들지 않았다.

## 고른 버전과 그 근거

제일 중요한 제약은 **CLAUDE.md가 `compileSdk 36` / `targetSdk 36`으로 못박았다**는 점이다.
2026년 10월 기준으로 androidx 최신 버전들은 이미 `compileSdk 37`을 요구하기 시작했기 때문에,
“최신 안정판”을 그냥 쓰면 빌드가 깨진다. 그래서 **각 라이브러리 AAR 안의
`META-INF/com/android/build/gradle/aar-metadata.properties`를 직접 열어 `minCompileSdk`를 확인**하고,
36 이하인 것들 중 가장 최신을 골랐다.

| 항목 | 고른 버전 | 근거 |
| --- | --- | --- |
| Gradle | 9.5.1 | AGP 8.13의 최소 요구(8.13)를 넘고, Roborazzi 1.77.0 저장소가 실제로 쓰는 조합 |
| AGP | 8.13.2 | 8.x 계열 마지막 안정판. [릴리스 노트](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes): 최대 API 36.1, 최소 Gradle 8.13, 최소 JDK 17 |
| Kotlin | 2.2.21 | Compose 컴파일러 플러그인 2.3 이상은 AGP 9.2 이상을 요구(ComposeAgpMappingFile). Compose 1.11에는 AGP 9가 필요 없으므로 2.2.x 로 맞췄다 |
| Compose 컴파일러 플러그인 | 2.2.21 | Kotlin 버전과 동일하게 맞추는 것이 [공식 안내](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) |
| Compose BOM | 2026.06.01 | `ui 1.11.4` / `material3 1.4.0`. 다음 BOM(2026.08.00)부터 `ui 1.12.0`인데 **1.12는 `minCompileSdk=37`** 이라 못 쓴다 |
| androidx.core:core-ktx | 1.18.0 | 1.19.1은 `minCompileSdk=37` |
| androidx.activity:activity-compose | 1.13.0 | `minCompileSdk=36` (통과) |
| androidx.lifecycle | 2.10.0 | 2.11.0은 `minCompileSdk=37` |
| androidx.navigation:navigation-compose | 2.9.8 | 2.10.2는 `minCompileSdk=37` |
| Robolectric | 4.17 | API 23~37 지원 |
| Roborazzi | 1.77.0 | 최신 안정판 |
| androidx.test.ext:junit | 1.3.0 | 최신 안정판 |
| JDK | 로컬·CI 모두 21 | AGP 8.13 최소 JDK 17 |

확인한 실제 값(참고):

```
androidx.compose.ui:ui-android:1.11.4 → minCompileSdk=35, minAndroidGradlePluginVersion=8.6.0
androidx.compose.ui:ui-android:1.12.1 → minCompileSdk=37, minAndroidGradlePluginVersion=9.1.0
androidx.navigation:navigation-compose-android:2.9.8  → minCompileSdk=35
androidx.navigation:navigation-compose-android:2.10.2 → minCompileSdk=37
androidx.core:core-ktx:1.18.0 → minCompileSdk=36
androidx.core:core-ktx:1.19.1 → minCompileSdk=37
```

### AGP 9가 아니라 8.13.2를 고른 이유

AGP의 현재 최신 안정판은 **9.4.1**이고 compileSdk 36도 지원한다. 그래도 8.13.2를 골랐다.

1. **AGP 9는 Kotlin 플러그인 적용 방식을 바꿨다.** 9.0부터 Kotlin 지원이 AGP에 내장되고
   `org.jetbrains.kotlin.android` 플러그인은 새 DSL과 호환되지 않는다
   ([AGP 9.0 릴리스 노트](https://developer.android.com/build/releases/past-releases/agp-9-0-0-release-notes)).
   그런데 AGP 9.4.1의 POM이 끌고 오는 Kotlin은 2.2.10이다. 즉 AGP 9로 가도 Kotlin은 더 낮아진다.
2. **Roborazzi가 아직 AGP 9에서 검증되지 않았다.** Roborazzi 1.77.0 저장소의 `libs.versions.toml`은
   AGP 8.13.2 / Gradle 9.5.1 / compileSdk 36을 쓰고, Compose 컴파일러를 2.2.x에 고정해 두었다.
   1단계의 핵심 산출물이 스크린샷 테스트라서 여기서 모험할 이유가 없다.
3. 2단계에서 붙일 `google-services` 플러그인도 AGP 9 새 DSL 대응 여부를 따로 확인해야 한다.

**그래서 `./gradlew lint`는 아래 경고 8개를 계속 낸다. 전부 일부러 고정한 버전이고, 오류는 0개다.**
`compileSdk 37`로 올릴 때(= Compose 1.12 이상으로 갈 때) 한꺼번에 풀면 된다.

- `AndroidGradlePluginVersion` (AGP 9.4.1 있음) × 1
- `GradleDependency` (compose-bom / core-ktx / lifecycle / navigation) × 4
- `NewerVersionAvailable` (Kotlin 2.4.21 있음) × 2
- `ObsoleteSdkInt` (`mipmap-anydpi-v26`) × 1 — 아래 ‘막힌 점’ 참고

## 막힌 점

### 1. Robolectric 4.17 + JDK 21에서 모든 테스트가 실패

```
java.lang.RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?
Caused by: java.lang.IllegalAccessException: class org.robolectric.interceptors.AndroidInterceptors$FileDescriptorInterceptor
  cannot access class jdk.internal.access.SharedSecrets (in module java.base)
  because module java.base does not export jdk.internal.access to unnamed module
```

SDK 36 환경을 만들 때 `ApplicationSharedMemory.create()`가 불리고, 그 안에서 Robolectric이
JDK 내부 클래스를 리플렉션으로 쓴다. JDK 17 이상에서는 기본적으로 막혀 있다.
`app/build.gradle.kts`에서 테스트 JVM에 플래그를 하나 줘서 해결했다.

```kotlin
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
```

`public` 메서드만 부르므로 `--add-opens`가 아니라 `--add-exports`면 충분하다.

### 2. 스크린샷 PNG가 `app/` 바로 밑에 떨어졌다

`roborazzi { outputDir }`를 `docs/screenshots/01`로 지정했는데도
`captureRoboImage("01-home.png")`가 만든 파일은 테스트 작업 디렉터리인 `app/`에 생겼다.
Roborazzi의 기본 경로 전략이 `relativePathFromCurrentDirectory`이기 때문이다.
`gradle.properties`에 아래를 넣어 해결했다.

```properties
roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory
```

같이 넣은 `roborazzi.test.record=true` 덕분에 `./gradlew testDebugUnitTest`만 돌려도 PNG가 갱신된다.

### 3. lint의 `ObsoleteSdkInt` 제안을 따르면 빌드가 깨진다

lint가 “`minSdkVersion`이 26이니 `mipmap-anydpi-v26`을 `mipmap-anydpi`로 합치라”고 한다.
그대로 했더니 AGP 리소스 병합에서 그 폴더가 통째로 빠져
`AAPT: error: resource mipmap/ic_launcher not found`로 빌드가 실패했다.
`-v26`을 유지하고 경고를 남겨 두기로 했다(파일에 이유를 주석으로 적어 두었다).

### 4. 그 밖에

- Compose `createComposeRule()`이 deprecated라는 경고가 뜬다. 대안인 `...junit4.v2.createComposeRule`은
  디스패처가 `StandardTestDispatcher`로 바뀌어서 동기화를 더 신경 써야 한다. 1단계에서는
  안정적인 쪽을 두고 넘어간다. → 남은 일
- Gradle이 “Gradle 10과 호환되지 않는 기능을 썼다”는 deprecation 경고를 낸다. 우리 스크립트가 아니라
  플러그인 쪽에서 나온다(`--warning-mode all`로 확인 가능). 지금은 그대로 둔다.

## 남은 일

- [ ] `createComposeRule()` → `androidx.compose.ui.test.junit4.v2.createComposeRule` 이전 검토.
- [ ] 설정 화면의 다섯 항목은 지금 누를 수 없다(자리만). 3~6단계에서 하나씩 연결.
- [ ] ‘그룹 만들기’ 버튼은 아직 아무것도 하지 않는다 → 2단계.
- [ ] 다크 테마 스크린샷은 아직 안 찍는다. 화면이 늘어나면 같이 추가.
- [ ] Gradle 구성 캐시(`org.gradle.configuration-cache`)는 아직 켜지 않았다.
      Roborazzi 플러그인과 같이 돌려 보고 문제없으면 켠다.
- [ ] `compileSdk 37`로 올리면서 AGP 9 · Kotlin 2.4 · Compose BOM 최신으로 한 번에 올리는 작업.
      이때 Roborazzi의 AGP 9 지원 여부를 먼저 확인해야 한다.
- [ ] 2단계에서 Firebase를 붙일 때 CI에 가짜 `google-services.json`을 만드는 단계를 추가.

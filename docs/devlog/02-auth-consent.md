# 2단계 — 회원가입·동의·계정 삭제, 그룹과 초대

작업일: 2026-10-24 ~ 2026-10-25 · 브랜치: `feature/02-auth-consent`

> 이 단계는 작업 도중 세 번 끊겼다(10-24 07:58, 10-24 14:03, 10-25 10:50. 모두 PC가 꺼지면서
> 실행 프로세스가 종료됐다). 파일은 그대로 남아 있어서 매번 `git status` 로 확인하고 이어서 했다.
> 첫 두 번은 커밋이 하나도 없는 상태에서 끊겨서 아찔했다. 세 번째 이후로는 **빌드가 되는 단위마다
> 바로 커밋**하는 식으로 바꿨다. 아래 '막힌 점'의 첫 두 항목은 그 사이에 찾아낸 문제다.

## 한 일

### Firebase 연동

- Firebase BoM, Authentication, Cloud Firestore 를 붙였다. `google-services` 플러그인은
  **`app/google-services.json` 이 있을 때만** 적용한다. 없으면 플러그인을 건너뛰고,
  앱은 죽지 않고 '서버 설정이 필요합니다' 안내를 보여 준다.
- `scripts/fake-google-services.sh` 가 자리표시자 설정(`project_id=ansim-link-placeholder`)을 만든다.
  진짜 파일이 이미 있으면 건드리지 않는다. CI 는 빌드 전에 이 스크립트를 돌린다.
- `ServerConfig` 가 `project_id` 와 앱 ID 앞부분을 보고 '가짜 설정'을 알아낸다.
  가짜면 저장소를 아예 건드리지 않고 안내만 보여 준다.

### 회원가입·로그인

- 이메일·비밀번호 가입(인증 메일 발송), 로그인, 비밀번호 재설정.
- Google 로그인은 Credential Manager + Sign in with Google. Web client ID 는
  `local.properties` 의 `googleWebClientId` 또는 환경 변수 `GOOGLE_WEB_CLIENT_ID` 로 받고,
  없으면 버튼을 숨긴다.
- 가입 전에 **만 14세 이상 확인 + 이용약관 + 개인정보 처리방침 + 위치기반서비스 이용약관**
  네 가지를 각각 체크해야 한다. '전체 동의' 버튼이 있지만 항목별 체크를 항상 같이 보여 준다.
  줄 전체를 눌러야 체크되고, 뒤로 가기로는 절대 바뀌지 않는다.
- 동의 항목·시각·약관 버전을 `users/{uid}.consents` 에 남긴다.

**Google 로그인도 동의를 먼저 받는다.** 버튼을 누르기 전에는 이 사람이 새 계정인지 알 수 없는데,
새 계정이면 동의 없이 계정이 만들어져 버린다. 그래서 로그인 화면에서도 동의 묶음을 보여 준다.
혹시 동의 없이 새 계정이 만들어지면 저장소가 그 계정을 바로 지운다(이중 안전장치).

### 계정 삭제

설정 > 계정 > 계정 삭제에서 **재인증 → 내가 만든 그룹 처리 선택 → 영구 삭제** 순서로 진행한다.

- 재인증은 이메일 계정이면 비밀번호, Google 계정이면 Google 재로그인.
- 내가 만든 그룹에 다른 구성원이 남아 있으면 **그룹 해산** 또는 **소유권 넘기기** 중 하나를 고른다.
  나 혼자인 그룹은 묻지 않고 지운다.
- 삭제 순서는 되돌릴 수 없는 것을 마지막에 둔다. 그룹 정리 → `users/{uid}`·푸시 토큰 → Auth 계정.
- 구독 해지 안내와 Google Play 구독 관리 링크를 같이 보여 준다(결제 코드는 5단계).
- 앱 밖 삭제 요청용 문서 `docs/account-deletion.md` 를 만들었다.

### 그룹과 초대

- 그룹 만들기, 초대 코드(6자리, 24시간) 발급, 코드로 참여.
- 참여할 때 **위치 제공 동의 화면**에서 제공받는 사람 목록·목적·기간을 보여 주고,
  체크해야만 참여된다(위치정보법 제19조②).
- 그룹 화면에 구성원 목록과 각자의 공유 상태(동의함/일시중지/동의 철회)를 **모두에게** 보여 준다.
- '내 공유 상태'에서 일시중지와 동의 철회를 한 번에 할 수 있다(제24조).
  철회하면 그룹에서 나가고 내 구성원 문서와 위치를 지운다.
- 무료 한도(그룹 1개, 구성원 3명)를 상수로 두고 넘으면 안내한다.

### Firestore 구조와 보안 규칙

```
users/{uid}                        displayName, email, consents{...}, createdAt
users/{uid}/pushTokens/{token}     (4단계) 계정 삭제 때 같이 지운다
groups/{gid}                       name, ownerUid, memberUids[], createdAt
groups/{gid}/members/{uid}         displayName, consent{location, at, version}, paused, joinedWithCode
groups/{gid}/invites/{code}        createdBy, createdAt, expiresAt
groups/{gid}/locations/{uid}       (4단계)
inviteCodes/{code}                 groupId, groupName, memberNames[], expiresAt, createdBy
```

`tests/rules/` 에 `@firebase/rules-unit-testing` 테스트 **32개**를 두었고,
이 PC에서 Firestore 에뮬레이터로 **전부 통과**하는 것을 확인했다(Node 24, JDK 21).

### 화면과 테스트

- 화면은 모두 `UiState` 와 콜백만 받는 함수다. ViewModel 이 저장소를 부른다.
  덕분에 Firebase 없이 미리보기와 스크린샷을 찍을 수 있다.
- 단위 테스트 **78개** 통과. 스크린샷 9장을 `docs/screenshots/02/` 에 만들었다.
- `./gradlew lint testDebugUnitTest assembleDebug` 통과. lint 오류 0개, 경고 12개
  (전부 1단계에서 일부러 고정한 버전 관련 경고다. 새로 늘어난 경고는 없다).

## 고른 버전과 그 근거

1단계와 같은 방법으로 골랐다. 그런데 이번에는 **제약이 하나 더 있었다.**

1단계에서 Kotlin 을 2.2.21 로 묶었다(Compose 컴파일러 플러그인 2.3 이상이 AGP 9.2 이상을 요구해서).
그런데 **Kotlin 2.2 컴파일러는 `.kotlin_module` 메타데이터 2.3.0 까지만 읽는다.**
최신 Firebase BoM 35.1.0 의 `firebase-auth 25.0.0` 은 메타데이터가 2.4.0 이라 컴파일이 깨진다.

```
e: ... firebase-auth-25.0.0-api.jar!/META-INF/....kotlin_module
   Module was compiled with an incompatible version of Kotlin.
   The binary version of its metadata is 2.4.0, expected version is 2.2.0.
```

그래서 각 AAR 의 `classes.jar` 안 `META-INF/*.kotlin_module` **첫 바이트를 직접 읽어**
메타데이터 버전을 확인했다. 파일 머리는 `[개수][major][minor][patch]` 를 4바이트 정수로 담고 있다.

```
$ unzip -p firebase-auth-25.0.0.aar classes.jar > c.jar
$ unzip -p c.jar "META-INF/*.kotlin_module" | od -An -tu1 -N16
   0   0   0   3   0   0   0   2   0   0   0   4   0   0   0   0
                      ^^^^^^^^^^^^^^^^^^^^^^^^^  = 2.4.0
```

| 항목 | 고른 버전 | 메타데이터 | 근거 |
| --- | --- | --- | --- |
| Firebase BoM | **34.19.0** | auth 2.3.0 / firestore 1.9.0 / common 1.9.0 | 35.x 는 auth 가 2.4.0 이라 못 쓴다. 34.19.0 이 auth 24.2.0 을 주는 마지막 BoM |
| google-services 플러그인 | 4.5.0 | — | 최신. AGP 8.13.2 에서 동작 확인 |
| androidx.credentials | 1.6.0 | 2.1.0 | `minCompileSdk=35` (통과) |
| credentials-play-services-auth | 1.6.0 | — | `minCompileSdk=35` |
| googleid | **1.2.0** | 2.1.0 | 1.2.1 은 2.4.0 이라 못 쓴다 |
| kotlinx-coroutines-play-services | 1.11.0 | — | `Task.await()` |
| lifecycle-runtime/viewmodel-compose | 2.10.0 | — | 1단계에서 고른 lifecycle 과 같은 버전 |

`firebase-auth 24.2.0` 의 메타데이터는 2.3.0 인데 이건 통과한다.
Kotlin 컴파일러는 자기 버전의 **다음 마이너까지** 읽어 주기 때문이다(2.2 → 2.3 까지 OK, 2.4 는 거부).

## 막힌 점

### 1. 블록 주석 하나 때문에 의존성이 통째로 사라졌다 (제일 오래 걸림)

빌드가 이렇게 깨졌다.

```
e: AppContainer.kt:5:8 Unresolved reference 'com'.
e: MainActivity.kt:10:22 Unresolved reference 'ComponentActivity'.
```

`androidx.activity`, `com.google.firebase` 가 전부 없다는 것이다. `clean` 도, 데몬 재시작도,
`.gradle` 삭제도 소용없었다. 확인해 보니 의존성이 **정말로** 하나도 없었다.

```
$ ./gradlew :app:dependencies --configuration implementation
implementation - Implementation only dependencies for 'main' sources. (n)
No dependencies
```

`dependencies { }` 블록 안에 로그를 넣어도 찍히지 않았다. 그런데 스크립트 맨 위에 `error(...)` 를
넣으면 빌드는 실패했다. 즉 **파일은 읽히는데 뒷부분이 실행되지 않았다.**
로그를 하나씩 옮겨 가며 범위를 좁혔더니 멈추는 지점이 이 주석이었다.

```kotlin
/**
 * docs/legal/*.md 를 res/raw 로 복사한다.
 */
```

**Kotlin 의 블록 주석은 중첩된다.** (Java 와 다르다.)
`docs/legal/` 다음에 오는 `*` 가 `/*` 가 되어 주석이 한 겹 더 열리고,
닫는 `*/` 하나로는 둘 다 닫히지 않는다. 그래서 그 아래 `dependencies { }` 까지 **전부 주석**이 됐다.

제일 고약한 점은 **빌드가 성공한다**는 것이다. 의존성이 하나도 없는 상태로 설정이 끝나고,
컴파일 단계에 가서야 "`com` 을 찾을 수 없다"는 엉뚱한 오류로 나타난다.

같은 문제가 `LegalDocumentResources.kt` 에도 있었다. 거기서는 `Unclosed comment` 로 잡혔다.
지금은 두 곳 다 **줄 주석(`//`)** 으로 바꾸고, 왜 그래야 하는지 주석에 적어 두었다.

### 2. 생성한 리소스를 AGP 에 등록하는 방법

약관 마크다운을 `docs/legal` 에서 `res/raw` 로 복사하는 태스크를 만들었다.
처음에는 `Copy` 태스크 + `sourceSets.main.res.srcDir(경로)` 로 했는데 이렇게 실패했다.

```
Task ':app:mapDebugSourceSetPaths' uses this output of task ':app:copyLegalDocuments'
without declaring an explicit or implicit dependency.
```

이 폴더를 읽는 태스크가 `mergeResources` 말고도 여럿이라 하나씩 `dependsOn` 을 다는 건 답이 아니었다.
결국 `DirectoryProperty` 출력을 가진 태스크로 바꾸고 **Variant API** 로 등록했다.

```kotlin
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            copyLegalDocuments,
            CopyLegalDocuments::outputDir,
        )
    }
}
```

이러면 출력 폴더를 AGP 가 정해 주고, 그 폴더를 읽는 모든 태스크가 자동으로 의존하게 된다.

### 3. 초대 코드를 받은 사람은 그룹을 읽을 수 없다

참여 동의 화면에는 "이 그룹 구성원(이름 목록)에게 제공됩니다"를 보여 줘야 한다(제19조②).
그런데 코드를 받은 사람은 **아직 구성원이 아니라서** `groups/{gid}` 를 읽을 수 없다.

지시문의 구조대로 `groups/{gid}/invites/{code}` 만 두면, 코드만 가지고 그룹을 찾으려면
`invites` 를 컬렉션 그룹으로 조회해야 한다. 그런데 보안 규칙으로 '코드를 아는 사람만'을
표현할 방법이 없다. 목록 조회를 열어 주는 순간 아무나 **모든 초대 코드를 훑을 수 있다.**

그래서 최상위 `inviteCodes/{code}` 를 하나 더 두었다. **문서 ID 가 곧 코드**라서
단건 조회(`get`)만 열어 주면 '코드를 아는 사람만' 읽을 수 있고, 목록 조회(`list`)는 막는다.
이 문서에는 동의 화면에 법적으로 보여 줘야 하는 것(그룹 이름, 구성원 이름)만 담는다.
`groups/{gid}/invites/{code}` 는 그대로 두었다(그룹을 만든 사람이 발급 내역을 보고 지우는 용도).

구성원 이름은 **코드를 만든 시점의 목록**이라서 화면에 그렇게 적어 두었다.
참여한 뒤에는 그룹 화면에서 지금 구성원을 언제든 볼 수 있다.

### 4. 배치 쓰기는 '쓰기 전 상태'로 평가된다

그룹 참여를 한 번의 배치로 처리하려다 규칙에 걸렸다.
Firestore 보안 규칙은 배치 안의 각 쓰기를 **배치 전 상태**로 평가한다. 그래서

- `memberUids` 에 나를 더하는 규칙이 "내 구성원 문서가 있는지" 보는데,
- 같은 배치에서 그 문서를 만들면 아직 없는 것으로 보인다.

결국 **두 번에 나눠** 쓴다. ① 구성원 문서 만들기(여기서 초대 코드를 검사) →
② `memberUids` 에 나를 더하기(여기서 ①을 확인). 그룹을 만들 때도 같은 이유로 나눠 쓴다.
중간에 끊기면 구성원 문서만 있고 목록에는 없는 상태가 되는데, 다시 참여하면 복구된다.

### 5. Roborazzi: 기본이 아닌 화면 크기에서 하단 탭이 두 번 그려진다

긴 화면을 찍으려고 `@Config(qualifiers = "...h1150dp...")` 로 화면을 늘렸더니,
`Scaffold` 의 하단 탭이 **화면 위쪽에 한 번 더** 그려진 이미지가 나왔다.
테스트 안에서 `RuntimeEnvironment.setQualifiers` 로 크기를 바꿀 때도 같은 일이 생겼다.

기본 크기(1단계가 쓰는 915dp)에서는 멀쩡하다. 원인을 끝까지 파지는 못했고,
**긴 화면은 하단 탭 없이 화면 본문만 찍는 것**으로 피해 갔다.
하단 탭이 보이는 그림은 1단계 스크린샷이 기본 크기로 찍어 준다. 테스트 주석에 적어 두었다.

### 6. 그 밖에

- `scripts/fake-google-services.sh` 를 **이 환경에서는 실행해 볼 수 없었다**(샌드박스가 스크립트
  실행을 막는다). 대신 스크립트가 만드는 JSON 을 추출해서 지금 빌드에 쓰는 파일과 바이트 단위로
  같은지 확인하고, JSON 파싱과 `project_id`·패키지 이름이 앱의 `ServerConfig` 상수와 맞는지 확인했다.
  실제 실행은 CI 에서 처음 검증된다.
- 스크립트의 실행 권한(`100755`)을 깜빡해서 한 번 놓칠 뻔했다. 644 로 커밋하면 CI 에서
  `Permission denied` 가 난다.

## 남은 일

- [ ] 1단계 스크린샷(`docs/screenshots/01/`)이 2단계 화면 변경으로 같이 갱신됐다.
      1단계 글에 쓴 이미지를 그대로 두고 싶으면 그 커밋에서 되살려야 한다.
- [ ] `firestore.rules` 를 실제 프로젝트에 배포하는 절차는 아직 문서로만 있다.
      (`firebase deploy --only firestore:rules`)
- [ ] 약관 3개와 계정 삭제 문서의 `[사업자 상호]` 같은 자리표시자를 채우고 법률 검토를 받아야 한다.
      6단계(출시 준비)에서 한다.
- [ ] 이메일 인증을 아직 강제하지 않는다. 인증하지 않아도 그룹을 만들 수 있다.
      강제할지, 어디까지 막을지는 더 생각해 봐야 한다.
- [ ] `users/{uid}` 문서가 Google 로그인 첫 로그인 때만 만들어진다. 중간에 실패하면
      동의 기록 없는 계정이 남을 수 있다. 서버(Cloud Functions)에서 정리하는 쪽이 맞다 → 4단계.
- [ ] 그룹 소유자는 그룹 화면에서 바로 나갈 수 없고 계정 삭제 화면을 거쳐야 한다.
      그룹 화면에도 '소유권 넘기기'를 두는 편이 자연스럽다.
- [ ] 초대 코드 발급 내역을 보고 지우는 화면이 없다. 지금은 만들기만 된다.
- [ ] 다크 테마 스크린샷은 아직 안 찍는다(1단계부터 남은 일).

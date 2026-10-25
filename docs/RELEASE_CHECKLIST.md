# 출시 체크리스트

Google Play 에 안심연결을 내보내기까지 **사람이 해야 하는 일**을 모았다.
코드로 할 수 있는 것은 6단계에서 다 해 두었고, 여기 남은 것은 계정·계약·신고·실기기처럼
저장소 안에서 끝낼 수 없는 것들이다.

> **지금 상태: 아무것도 하지 않았다.** 이 저장소에는 Play Console 계정도, Firebase Blaze
> 프로젝트도, 실기기도, 서명 키도 없다. 아래 체크박스는 **전부 비어 있는 것이 맞다.**
> 하지 않은 것을 '완료'로 적지 않는다.
>
> 각 항목의 `확인 필요` 는 **공식 문서에서 확인하지 못한 것**이다. 추측으로 적지 않고
> 어디에 물어볼지만 적어 두었다.

## 0. 기한이 있는 것 (먼저 본다)

| 항목 | 기한 | 지금 | 메모 |
| --- | --- | --- | --- |
| **대상 API 36** | 연장 신청 기한 **2026-11-01** | `targetSdk = 36` ✔ | 이미 36 이라 연장 신청이 필요 없다 |
| **Play Billing Library 8 이상** | 연장 신청 기한 **2026-11-01** | `billing = 9.1.0` ✔ | 2026-08-31 부터 8 이상이어야 한다. 9.1.0 이라 충족 |

두 기한이 같은 날(2026-11-01)이다. 둘 다 이미 충족한 상태이므로 **연장 신청을 할 일이
없다.** 다만 라이브러리를 되돌리거나 `targetSdk` 를 내리면 그 순간 걸린다.

- [ ] Play Console 에 앱을 만든 뒤 '앱 번들 탐색기'에서 대상 API 36 으로 인식되는지 확인
- [ ] Billing 버전 경고가 Console 에 뜨지 않는지 확인

## 1. 법령 신고 (국내)

### 1-1. 위치기반서비스사업 신고 — **확인 필요**

이 앱은 개인위치정보를 수집해 그룹 구성원에게 제공하므로 위치정보법의 적용을 받는다.
그런데 **어느 신고·등록이 필요한지 확정하지 못했다.**

| 구분 | 근거 | 이 앱이 해당하는지 |
| --- | --- | --- |
| 위치정보사업 **허가/등록** | 위치정보법 제5조 | **확인 필요** |
| 위치기반서비스사업 **신고** | 위치정보법 제9조 | **확인 필요** (해당할 가능성이 높다고 보지만 확정하지 못했다) |
| 소규모 위치기반서비스사업 **신고 간소화/면제** | 위치정보법 제9조의2 | **확인 필요** |

**왜 확정하지 못했나.** 쟁점은 '단말기에서 수집한 개인위치정보를 사업자 서버를 거쳐
다른 이용자에게 전달하는 것'이 제5조의 위치정보사업(위치정보를 수집하여 제공하는 사업)에
해당하는지다. 이 앱은 위치를 직접 측위하지 않고(단말의 Fused Location Provider 가 준
값을 쓴다) Firestore 를 거쳐 같은 그룹 구성원에게 보낸다. 해석이 갈릴 수 있는 자리라
공식 문서만으로 결론을 내지 않았다.

- [ ] **위치정보지원센터에 문의한다** — <https://www.lbsc.kr> / 02-588-0185
      물어볼 것: ① 제5조 위치정보사업 등록 대상인지 ② 제9조 위치기반서비스사업
      신고로 되는지 ③ 제9조의2 소규모 사업자 신고가 적용되는지 ④ 개인(비사업자)도
      신고할 수 있는지, 사업자등록이 먼저 필요한지
- [ ] 결론을 이 문서에 적고, [위치기반서비스 이용약관](legal/location-terms.md) 제2조의
      `[위치기반서비스사업 신고번호 또는 신고 예정 표시]` 와
      [이용약관](legal/terms-of-service.md) 제2조를 채운다
- [ ] 신고가 필요하면 방송미디어통신위원회에 신고하고 신고번호를 받는다(위치정보법 제9조. 2025. 10. 1. 개정으로 소관 명칭이 바뀌었다)
- [ ] 받은 신고번호를 약관 → `./gradlew syncSiteDocuments` → 앱 빌드 순서로 반영한다
      (약관은 docs/legal 이 원본이고 앱 리소스와 공개 사이트가 거기서 만들어진다)

문의 결과 기록란:

```
문의일:
문의 방법:
답변 요지:
결론: (제5조 / 제9조 / 제9조의2 / 해당 없음)
신고번호:
```

### 1-2. 통신판매업 신고 — **확인 필요**

앱 안에서 월 구독(`premium_monthly`)을 판다. 전자상거래법 제12조는 통신판매업자에게
신고 의무를 두지만, 같은 조 단서와 **공정거래위원회 고시 제2022-4호**가 면제 기준을 둔다.

| 면제 기준 | 이 앱 |
| --- | --- |
| 직전년도 통신판매 거래 횟수 **50회 미만** | 출시 전이라 0회. 첫 해에는 면제 범위에 들 가능성이 높다 |
| **간이과세자**(부가가치세법 제61조) | 사업자 등록 형태에 따라 다르다 |

- [ ] **확인 필요**: Google Play 를 통한 앱 내 구독이 '통신판매'에 해당하는지, 그리고
      거래 횟수를 Play 의 결제 건수로 세는지. Google 이 결제를 대행하는 구조에서
      누가 통신판매업자인지가 쟁점이다. 공정거래위원회 또는 관할 구청
      일자리경제과(통신판매업 신고 담당)에 문의한다
- [ ] 거래가 50회에 가까워지면 다시 본다(면제 기준을 넘는 순간 신고 의무가 생긴다)
- [ ] 신고하면 신고번호를 [이용약관](legal/terms-of-service.md) 제2조에 적는다

### 1-3. 개인정보 보호

- [ ] 개인정보 보호책임자(이름·직책·연락처)를 정해
      [개인정보 처리방침](legal/privacy-policy.md) 9항에 적는다
- [ ] 위치정보 관리책임자를 정해 [위치기반서비스 이용약관](legal/location-terms.md)
      제13조에 적는다 (같은 사람이어도 되지만 두 자리를 각각 적어야 한다)
- [ ] 약관 3종과 계정 삭제 안내의 `[ ]` 자리표시자를 모두 채운다
      — 사업자 상호, 대표자, 주소, 전화, 전자우편, 사업자등록번호, 공지 URL
- [ ] **법률 검토를 받는다.** 지금 문서는 법령 조문을 읽고 쓴 초안이고 변호사 검토를
      받지 않았다. 각 문서 머리말에 그렇게 적혀 있다
- [ ] 시행일을 정해 세 문서의 '시행일' 과 `TermsVersion` 상수를 맞춘다
      (`domain/consent/TermsVersion.kt` — 버전을 바꾸면 기존 사용자에게 재동의를 받는
      흐름이 필요한지도 같이 생각한다)

## 2. 공개 URL

Play Console 과 앱이 URL 로 가리키는 문서들이다. 공개되지 않으면 심사가 진행되지 않는다.

- [ ] GitHub Pages 를 켠다 — Settings > Pages > Source 를 **GitHub Actions** 로 바꾼다
      (`.github/workflows/pages.yml` 이 `docs/site` 만 빌드한다. 켜지 않으면 배포 단계가 실패한다)
- [ ] `pages.yml` 을 한 번 실행해 사이트가 뜨는지 확인한다 (지금까지 실행해 본 적이 없다)
- [ ] **개인정보 처리방침 URL**: `https://<사용자>.github.io/ansim-link/privacy-policy/`
      → Play Console > 앱 콘텐츠 > 데이터 보안, 스토어 등록정보
- [ ] **계정·데이터 삭제 URL**: `https://<사용자>.github.io/ansim-link/account-deletion/`
      → Play Console > 데이터 보안 > 데이터 삭제
- [ ] 위치기반서비스 이용약관 URL, 이용약관 URL 도 같이 확인
- [ ] 약관 안의 `[공지 URL]`, `[문서 사이트 URL]` 을 실제 주소로 채운다
- [ ] 앱 화면의 약관 링크와 공개된 문서가 **같은 내용**인지 확인
      → `./gradlew testDebugUnitTest` 의 `SiteDocumentsTest` 가 자동으로 본다.
      사람은 '공개된 주소가 실제로 열리는지'만 확인하면 된다

기록란:

```
Pages 켠 날:
처리방침 URL:
계정 삭제 URL:
약관 URL:
```

## 3. Firebase

- [ ] Firebase 프로젝트를 만들고 Android 앱(`io.github.ndrcb5210max.ansimlink`)을 등록한다
- [ ] **실제 `google-services.json`** 을 받아 `app/` 에 둔다
      — 저장소에 커밋하지 않는다(`.gitignore` 가 막는다). CI·릴리스 워크플로에는
      `GOOGLE_SERVICES_JSON` 시크릿으로 넣는다
      - 자리표시자로 만든 빌드는 Firebase 에 붙지 못하고 앱이 '서버 설정이 필요합니다'를
        보여 준다. **그 AAB 를 Play 에 올리면 안 된다**(release.yml 이 경고를 남긴다)
- [ ] Authentication 에서 이메일/비밀번호와 Google 로그인을 켠다
- [ ] Google 로그인 **Web client ID** 를 받아 `local.properties` 의 `googleWebClientId`
      또는 `GOOGLE_WEB_CLIENT_ID` 환경 변수/시크릿에 넣는다 (없으면 버튼이 숨는다)
- [ ] Firestore 를 만든다. 지역은 **`asia-northeast3`**(서울)
      — 다르면 `functions/src/index.ts` 의 `REGION` 을 맞춘다
- [ ] 보안 규칙을 배포한다: `firebase deploy --only firestore:rules`
      — 규칙 없이 열어 두면 남의 그룹 데이터를 읽을 수 있다. **가장 먼저 할 일이다**
- [ ] 처리방침 5항의 `[리전에 맞춰 기재]`(수탁자 국가)를 실제 리전으로 채운다

### 3-1. Blaze 전환과 Cloud Functions 배포

`functions/` 에 함수가 있지만 **한 번도 배포하지 않았다.** 배포하지 않으면

- 안심 메시지 문서는 만들어지지만 **푸시가 가지 않는다**(받는 쪽이 앱을 열어야 보인다),
- 공유가 끝난 위치가 **24시간 뒤에 지워지지 않는다**(CLAUDE.md 원칙 4 를 못 지킨다),
- `verifySubscription` 이 없어서 **프리미엄이 절대 열리지 않는다**(결제는 되고 한도는 안 열린다).

- [ ] **Blaze(종량제) 요금제로 전환한다** (2세대 함수는 Cloud Run·Artifact Registry·
      Cloud Build 를 쓰므로 Spark 에서는 배포가 멈춘다)
- [ ] **예산 알림을 먼저 걸어 둔다** — Google Cloud Console > 결제 > 예산 및 알림.
      무료 할당량이 있지만 넘으면 과금된다
- [ ] `cd functions && npm install && npm run build`
- [ ] `firebase use <프로젝트-ID>` → `firebase deploy --only functions`
- [ ] 예약 함수(Cloud Scheduler 작업 2개)가 만들어졌는지 확인 (무료 3개 안에 든다)
- [ ] `functions/.env` 에 `ANDROID_PACKAGE_NAME` 등을 넣는다(`.env.example` 참고, 커밋 금지)
- [ ] 컬렉션 그룹 질의에 색인 오류가 나는지 확인
      (`functions/README.md` 의 '확인하지 못한 것'). 나면 `firestore.indexes.json` 을 추가
- [ ] 배포 뒤 실제로 푸시가 가는지, 위치가 24시간 뒤 지워지는지 확인

## 4. 서명 키

- [ ] **업로드 키스토어를 만든다** (저장소 밖에서)
      ```
      keytool -genkeypair -v -keystore upload-keystore.jks -storetype PKCS12 \
        -keyalg RSA -keysize 2048 -validity 10000 -alias upload
      ```
- [ ] **키스토어와 비밀번호를 안전하게 보관한다.** 잃으면 그 앱을 다시 업데이트할 수
      없다(Play 앱 서명을 쓰면 업로드 키는 재설정 요청이 가능하지만, 신청과 승인을 거쳐야
      한다). 비밀번호 관리자나 금고에 **두 군데 이상** 둔다
- [ ] 로컬에서 릴리스 빌드를 만들어 본다 — 환경 변수 네 개를 넣고
      ```
      ANSIM_KEYSTORE_PATH=/절대/경로/upload-keystore.jks
      ANSIM_KEYSTORE_PASSWORD=...
      ANSIM_KEY_ALIAS=upload
      ANSIM_KEY_PASSWORD=...
      ./gradlew bundleRelease
      ```
      그 뒤 **정말 서명됐는지 확인한다.** AAB 는 서명하지 않아도 파일 이름이 그대로라서
      눈으로는 구분되지 않는다
      ```
      unzip -l app/build/outputs/bundle/release/app-release.aab | grep "META-INF/.*\.RSA"
      jarsigner -verify app/build/outputs/bundle/release/app-release.aab
      ```
      `jarsigner` 에 **`-strict` 를 붙이지 않는다.** 업로드 키는 자체 서명이라
      신뢰 체인을 검증할 수 없고, `-strict` 는 그 경고를 0 이 아닌 종료 코드로 바꾼다
      (정상적으로 서명한 AAB 에서도 실패한다)
- [ ] GitHub 시크릿 4개를 넣는다 — `ANSIM_KEYSTORE_BASE64`, `ANSIM_KEYSTORE_PASSWORD`,
      `ANSIM_KEY_ALIAS`, `ANSIM_KEY_PASSWORD` (`.github/workflows/release.yml` 주석 참고)
- [ ] `versionCode` 를 올린다(`app/build.gradle.kts`, 지금 1). Play 는 같은 값을 두 번 받지 않는다
- [ ] 난독화 매핑 파일(`mapping.txt`)을 Play Console 에 올린다. 없으면 크래시 스택을 읽을 수 없다

### 4-1. Play 앱 서명 — Firebase 에 **앱 서명 키 지문**도 등록한다

새 앱은 AAB 를 올리면 **Play 앱 서명에 자동으로 등록**된다. 그러면 사용자 기기에
설치되는 APK 는 **Google 이 보관하는 앱 서명 키**로 서명되고, 우리가 만든 업로드 키는
Play 에 올릴 때만 쓰인다. 키가 두 개라는 뜻이다.

여기서 걸리는 것이 **Google 로그인**이다. Firebase 는 등록된 SHA 지문으로 앱을
확인하는데, 업로드 키 지문만 등록해 두면 **Play 에서 설치한 앱에서 Google 로그인이
실패한다.** 개발 중에는 잘 되다가 Play 로 받은 빌드에서만 안 되는, 원인을 찾기 어려운
증상이 된다.

- [ ] Play Console > 테스트 및 출시 > **설정 > 앱 서명**에서 **앱 서명 키**의
      SHA-1·SHA-256 지문을 복사한다
- [ ] Firebase Console > 프로젝트 설정 > 내 앱 > Android 앱에 그 지문을 **추가**한다
      (업로드 키 지문과 디버그 키 지문도 함께 두어야 개발 빌드가 계속 동작한다)
- [ ] `google-services.json` 을 **다시 받아** 교체한다(지문을 추가하면 파일이 바뀐다)
- [ ] Play 로 설치한 빌드에서 Google 로그인이 되는지 확인
      (근거: Play Console 고객센터 answer/9842756)

기록란:

```
업로드 키 SHA-1:
앱 서명 키 SHA-1:
앱 서명 키 SHA-256:
Firebase 등록일:
```

## 5. Play Console

제출할 글의 초안은 [docs/play/](play/) 에 있다.

- [ ] 개발자 계정을 만든다(등록비 25달러, 개인 계정은 신원 확인이 필요하다)
- [ ] 앱을 만든다 — 패키지 `io.github.ndrcb5210max.ansimlink`, 기본 언어 한국어
- [ ] 스토어 등록정보 ([store-listing.md](play/store-listing.md))
      - [ ] 앱 아이콘 512×512 **(없음 — 만들어야 한다)**
      - [ ] 그래픽 이미지 1024×500 **(없음 — 만들어야 한다)**
      - [ ] 스크린샷 2~8장 (Roborazzi 이미지의 비율·크기가 요건에 맞는지 확인)
- [ ] 데이터 보안 양식 ([data-safety.md](play/data-safety.md))
      — **비공개 테스트에도 필요하다**(answer/10787469). `확인 필요` 로 남긴 세 가지를
      Console 화면에서 정한다(위치 필수/선택, 대략적 위치, 비밀번호)
- [ ] 앱 콘텐츠 ([app-content.md](play/app-content.md))
      — 대상 연령, 콘텐츠 등급 설문, 광고 없음, **앱 액세스 권한(심사용 계정)**
- [ ] 백그라운드 위치 선언서와 포그라운드 서비스 선언
      ([permissions-declaration.md](play/permissions-declaration.md))
      - [ ] 시연 영상을 찍어 YouTube 미등록으로 올린다(30초 대본이 있다)
      - [ ] 영상에 ① 권한 요청 **전** 명시적 공개 ② 사용자가 공유를 시작하는 장면이
            들어 있는지 확인 (answer/9799150, answer/13392821)
- [ ] 구독 상품 `premium_monthly` 를 만들고 **활성화**한다
      (초안 상태면 앱에서 조회되지 않아 결제 화면에 금액이 안 뜬다)
- [ ] 라이선스 테스터를 등록한다 — 실제 결제 없이 결제 흐름을 돌릴 수 있다.
      사이드로드한 빌드로도 된다(5단계 devlog)
- [ ] Play Developer API 자격 증명 — Cloud Functions 의 기본 서비스 계정을 Play Console
      사용자로 초대해 '재무 데이터 보기' 권한을 준다(키 파일이 필요 없는 길,
      `functions/README.md` 방법 A)
- [ ] 실시간 개발자 알림(RTDN) — Pub/Sub 주제 `play-billing-notifications` 를 만들고
      Play Console 에 등록, '테스트 알림 보내기'로 배선 확인
- [ ] 내부 테스트로 먼저 올려 설치·로그인·결제가 되는지 본다
- [ ] 비공개 테스트 ([closed-testing-plan.md](play/closed-testing-plan.md))
      — 테스터 12명 이상 14일 연속
- [ ] 프로덕션 액세스 신청 (신청서 3개 항목 답변 초안이 있다)

## 6. 실기기 확인

에뮬레이터나 단위 테스트로 확인할 수 없는 것들이다. **하나도 하지 못했다.**

### 6-1. 재난문자 앱 패키지 이름 — 기록란

허용 목록에 세 개를 넣어 두었다(`domain/alert/AlertPackages.kt`).

```
com.google.android.cellbroadcastreceiver
com.android.cellbroadcastreceiver
com.android.cellbroadcastreceiver.module
```

**삼성 기기의 실제 패키지 이름을 확인하지 못했다.** 목록에 없으면 그 기기에서는
재난문자를 감지하지 못한다(알림이 와도 서비스가 곧바로 버린다). 1단계부터 남은 일이다.

확인 방법: 디버그 빌드 > 설정 > 받은 알림 패키지 기록. 재난문자가 온 뒤 그 화면에
남은 패키지 이름을 적는다. (릴리스 빌드에는 이 화면이 없다 — `BuildConfig.DEBUG`)

- [ ] 기록란을 채운다

| 기종 | Android 버전 | 통신사 | 재난문자 앱 패키지 이름 | 감지됨 | 확인일 |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |

- [ ] 새로 나온 패키지 이름을 `AlertPackages.kt` 의 허용 목록에 넣고 단위 테스트를 더한다
- [ ] 제목 문자열도 같이 확인한다(기기에 따라 띄어쓰기·표현이 다를 수 있다.
      분류기는 공백을 지우고 비교하지만 아예 다른 제목은 '종류 미상'이 된다)

### 6-2. 그 밖에 실기기에서 처음 확인되는 것

- [ ] 알림 접근 권한을 켜는 흐름 (사이드로드 빌드의 '제한된 설정' 안내가 맞는지)
- [ ] 자동 시작: 백그라운드에서 포그라운드 서비스 시작이 되는지,
      막혔을 때 '눌러서 시작' 알림으로 바뀌는지
- [ ] 공유 중 상시 알림이 기종별 알림 설정에서 숨겨지지 않는지
      (원칙 2 는 알림이 보인다는 전제에 기대고 있다)
- [ ] 배터리 최적화가 알림 리스너나 포그라운드 서비스를 죽이는지 (제조사별로 다르다)
- [ ] 위치 정확도와 50m·60초 규칙이 실제 이동에서 어떻게 동작하는지
- [ ] 실제 결제 흐름 전체 (5단계 devlog '시험할 순서' 7단계)
- [ ] 계정 삭제가 끝까지 되는지 (재인증 → 그룹 처리 → Auth 계정 삭제)

## 7. 출시 직전 마지막 확인

- [ ] `./gradlew lint testDebugUnitTest assembleDebug` 통과
- [ ] 서명된 AAB 를 만들고 `jarsigner -verify -strict` 로 확인
- [ ] 앱 안 약관 = 공개된 약관 (`SiteDocumentsTest` 통과 + 공개 URL 열어 보기)
- [ ] 약관·처리방침·계정 삭제 안내·앱 화면이 **같은 보유 기간(180일)** 을 말하는지
- [ ] `[ ]` 자리표시자가 남아 있지 않은지 — 공개되는 문서만 본다(devlog·prompts 에는 남아도 된다)
      ```
      grep -rn "\[사업자 상호\]\|\[고객문의 이메일\]\|\[처리방침 URL\]" \
        docs/legal docs/site docs/account-deletion.md
      ```
- [ ] 스토어 설명에 '감시', '몰래', '추적', '놓치지 않습니다' 같은 말이 없는지
- [ ] 스토어 설명에 '재난문자 감지를 보장하지 않는다'가 적혀 있는지
- [ ] 비밀 정보가 커밋되지 않았는지 — `git log -p` 에서 키스토어·서비스 계정 키·
      실제 `google-services.json` 이 없는지
- [ ] 태그를 붙이기 전에 `versionCode` 를 올렸는지
- [ ] `git tag v0.1.0 && git push origin v0.1.0` → release.yml 이 AAB 를 만든다

# 릴리스 빌드의 R8 규칙 (축소 + 난독화 + 리소스 축소).
#
# 기본은 proguard-android-optimize.txt 와 각 라이브러리가 AAR 에 넣어 준 consumer 규칙이다.
# 이 파일에는 그것으로 모자란 것만 적는다. **왜 필요한지 함께 적는다** — 근거 없는 keep 은
# 나중에 지워도 되는지 아무도 모르게 되고, 그동안 축소 효과만 깎는다.
#
# 반대로 '없어도 되는 이유'도 적어 둔다. 재난 상황에만 돌아가는 코드(알림 리스너,
# 포그라운드 서비스, WorkManager 작업)는 R8 이 잘못 지워도 **평소에는 아무 증상이 없다.**
# 릴리스 빌드에서 재난문자가 올 때까지 모른다. 그래서 '확인했다'를 글로 남긴다.

# 난독화된 스택트레이스를 매핑 파일로 되돌릴 수 있게 한다.
# 매핑 파일(app/build/outputs/mapping/release/mapping.txt)은 Play Console 에 올린다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# 추가 keep 규칙이 필요하지 않다고 본 것들 (확인한 근거)
# ─────────────────────────────────────────────────────────────────────────────
#
# 1) 매니페스트에 적은 구성 요소 — MainActivity, DisasterAlertListenerService,
#    LocationShareService, AlertActionReceiver, AnsimMessagingService,
#    AnsimLinkApplication.
#    AGP 가 병합된 매니페스트에서 keep 규칙을 자동으로 만들어 준다
#    (build/intermediates/aapt_proguard_file/release/aapt_rules.txt 에서 확인).
#
# 2) WorkManager 작업 — SafetyMessageWorker.
#    work-runtime 의 consumer 규칙(AAR 안 proguard.txt)이
#    `-keepnames class * extends androidx.work.ListenableWorker` 와
#    생성자 keep 을 넣어 준다. 클래스 자체는 OneTimeWorkRequestBuilder<SafetyMessageWorker>
#    에서 코드로 참조하므로 지워지지 않는다.
#
# 3) Room — AnsimDatabase 와 DAO·엔터티.
#    room-runtime 의 consumer 규칙이 `-keep class * extends androidx.room.RoomDatabase`
#    를 넣어 준다. 생성된 _Impl 클래스가 그 규칙에 걸린다. DAO 구현은 KSP 가 만든 코드가
#    직접 부르므로 리플렉션이 없다.
#
# 4) Firestore 문서 필드 — toObject()/POJO 매핑을 쓰지 않는다.
#    저장소 코드가 `get("필드이름")` 으로 문자열로 읽고 쓴다(5단계에서 그 이름을 못박는
#    테스트도 달았다). 그래서 난독화가 필드 이름을 바꿀 수 있는 자리가 없다.
#    (만약 나중에 데이터 클래스 매핑을 쓰면 그 클래스에 @Keep 을 달아야 한다.)
#
# 5) enum 을 이름으로 되살리는 코드가 없다.
#    발동 규칙·제공 목적처럼 저장해 두는 enum 은 `name` 대신 직접 정한 `key` 문자열을
#    쓴다(예: ProvisionPurpose.key). `valueOf`/`Class.forName` 을 쓰는 곳이 없다.
#
# 6) Play Billing — 순수 자바 라이브러리이고 consumer 규칙을 함께 넣어 준다.
#    앱은 응답을 리플렉션 없이 읽는다.
#
# ─────────────────────────────────────────────────────────────────────────────
# R8 이 경고한 '없는 클래스'
# ─────────────────────────────────────────────────────────────────────────────
#
# 아래는 라이브러리가 선택적으로만 쓰는 클래스라서 우리 빌드에 들어오지 않는다.
# missingclasses 보고서(build/outputs/mapping/release/missing_rules.txt)를 보고
# 하나씩 확인한 뒤 적었다. 쓰이지 않는 경로라 지워도 동작에 영향이 없다.

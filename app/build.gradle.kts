import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
}

// google-services 플러그인은 app/google-services.json 이 있어야만 동작한다(없으면 빌드가 실패한다).
// 이 저장소에는 실제 파일을 넣지 않으므로, 파일이 있을 때만 플러그인을 적용한다.
// CI 와 로컬에서는 `scripts/fake-google-services.sh` 로 가짜 파일을 만들어 두고 빌드한다.
// 파일이 아예 없으면 Firebase 가 초기화되지 않고, 앱은 '서버 설정이 필요합니다' 안내를 보여 준다.
val googleServicesJson = layout.projectDirectory.file("google-services.json").asFile
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.lifecycle(
        "[ansim-link] app/google-services.json 이 없어 google-services 플러그인을 건너뛴다. " +
            "`./scripts/fake-google-services.sh` 로 가짜 설정을 만들 수 있다.",
    )
}

// Google 로그인에 쓰는 Web client ID(OAuth 2.0 '웹 애플리케이션' 클라이언트).
//
// 비밀 값은 아니지만 프로젝트마다 다르므로 저장소에 넣지 않는다.
// `local.properties` 의 `googleWebClientId` 또는 환경 변수 `GOOGLE_WEB_CLIENT_ID` 에서 읽고,
// 없으면 빈 문자열이 되어 앱이 Google 로그인 버튼을 숨긴다.
val googleWebClientId: String = run {
    val fromProperties = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
        ?.getProperty("googleWebClientId")
    (fromProperties ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: "").trim()
}

// 릴리스 서명에 쓸 값 묶음. 네 개가 모두 있을 때만 만든다.
class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

// 릴리스 서명 설정은 환경 변수로만 받는다(6단계).
//
// 키스토어와 비밀번호는 저장소에 넣지 않는다(CLAUDE.md 원칙 6). .gitignore 가
// *.jks, *.keystore, keystore.properties 를 막아 두었지만 그것만 믿지 않는다 —
// 빌드 스크립트에 경로나 비밀번호를 적을 자리를 아예 두지 않는다.
//
// 값이 없으면 **빌드를 실패시키지 않고** 서명만 건너뛴다. CI 의 디버그 빌드와 다른 사람의
// 로컬 빌드는 키 없이 돌아야 한다.
//
// 주의: APK 는 서명을 건너뛰면 파일 이름에 `-unsigned` 가 붙지만 **AAB 는 붙지 않는다.**
// 서명 없이 만든 것도 `app-release.aab` 그대로다(직접 확인했다). 그래서 릴리스 워크플로
// (.github/workflows/release.yml)가 만든 뒤에 서명 블록이 있는지 따로 검사한다.
//
// 경로는 rootProject 기준으로 푼다(절대 경로는 그대로 쓴다). app/ 기준으로 풀면
// 같은 상대 경로가 명령을 어디서 실행했는지에 따라 다른 파일을 가리킨다.
val releaseSigning: ReleaseSigning? = run {
    fun read(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    val variableNames = listOf(
        "ANSIM_KEYSTORE_PATH",
        "ANSIM_KEYSTORE_PASSWORD",
        "ANSIM_KEY_ALIAS",
        "ANSIM_KEY_PASSWORD",
    )
    val values = variableNames.associateWith(::read)
    val missing = variableNames.filter { values[it] == null }

    when {
        // 하나도 없는 것은 평소 상태다. 경고로 띄우면 디버그 빌드마다 울려서
        // 정작 '일부만 설정됐다'는 경고가 묻힌다.
        missing.size == variableNames.size -> {
            logger.lifecycle(
                "[ansim-link] 릴리스 서명 환경 변수가 없어 릴리스 서명을 건너뛴다. " +
                    "필요한 값: " + variableNames.joinToString(", ") +
                    " (docs/RELEASE_CHECKLIST.md 참고)",
            )
            null
        }

        // 일부만 설정된 것은 거의 틀림없이 실수다(오타, CI 시크릿 누락). 경고로 알린다.
        missing.isNotEmpty() -> {
            logger.warn(
                "[ansim-link] 경고: 릴리스 서명 환경 변수가 일부만 설정됐다. 빠진 값: " +
                    missing.joinToString(", ") + ". 릴리스 서명을 건너뛴다.",
            )
            null
        }

        else -> {
            val keystore = rootProject.file(values.getValue("ANSIM_KEYSTORE_PATH")!!)
            if (keystore.isFile) {
                ReleaseSigning(
                    storeFile = keystore,
                    storePassword = values.getValue("ANSIM_KEYSTORE_PASSWORD")!!,
                    keyAlias = values.getValue("ANSIM_KEY_ALIAS")!!,
                    keyPassword = values.getValue("ANSIM_KEY_PASSWORD")!!,
                )
            } else {
                // 비밀번호는 로그에 찍지 않는다. 경로만 적는다.
                logger.warn(
                    "[ansim-link] 경고: ANSIM_KEYSTORE_PATH 가 가리키는 키스토어 파일이 없다 " +
                        "(" + keystore.absolutePath + "). 릴리스 서명을 건너뛴다.",
                )
                null
            }
        }
    }
}

android {
    namespace = "io.github.ndrcb5210max.ansimlink"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ndrcb5210max.ansimlink"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + googleWebClientId + "\"")
    }

    signingConfigs {
        // 환경 변수가 네 개 다 있을 때만 만든다. 빈 값으로 설정을 만들어 두면
        // 서명 단계에서 'password must not be null' 같은 오류로 뒤늦게 깨진다.
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 환경 변수가 없으면 null 이 되어 서명하지 않는다(위 releaseSigning 참고).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // GOOGLE_WEB_CLIENT_ID 를 코드에서 읽으려면 BuildConfig 가 필요하다.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric / Roborazzi 가 실제 리소스와 병합된 매니페스트를 읽어야 한다.
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        // 리포트를 CI 아티팩트로 올리기 쉽게 한곳에 모은다.
        htmlReport = true
        xmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// 약관 마크다운을 docs/legal 에서 res/raw 로 복사한다.
//
// 앱 화면에서 약관 전문을 보여 줘야 하는데, 같은 글을 두 군데 두면 반드시 어긋난다.
// 그래서 docs/legal 을 원본으로 삼고 빌드할 때마다 복사한다.
// 예: `terms-of-service.md` → `raw/legal_terms_of_service.md` (리소스 이름 `legal_terms_of_service`).
//
// 주의: 이 설명을 블록 주석(KDoc)으로 쓰면 안 된다. Kotlin 의 블록 주석은 중첩되기 때문에
// 주석 안의 `docs/legal/` 뒤에 별표가 오면 거기서 주석이 한 겹 더 열리고,
// 닫는 기호 하나로는 다 닫히지 않아 스크립트의 나머지(의존성 블록까지)가 통째로 주석이 된다.
// 그러면 빌드는 성공하는데 의존성이 하나도 없는 상태가 된다. 그래서 줄 주석만 쓴다.
// 출력 폴더를 AGP 가 정해 주도록 DirectoryProperty 를 가진 태스크로 만든다.
// 그래야 addGeneratedSourceDirectory 로 등록할 수 있고, 이 폴더를 읽는 모든 태스크가
// 자동으로 이 태스크에 의존한다(mergeResources, mapDebugSourceSetPaths 등).
abstract class CopyLegalDocuments : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val rawDirectory = outputDir.get().asFile.resolve("raw")
        rawDirectory.deleteRecursively()
        rawDirectory.mkdirs()
        sourceFiles.files.forEach { file ->
            // 리소스 이름에는 하이픈을 쓸 수 없다. terms-of-service.md → legal_terms_of_service.md
            file.copyTo(rawDirectory.resolve("legal_" + file.name.replace('-', '_')))
        }
    }
}

val copyLegalDocuments = tasks.register<CopyLegalDocuments>("copyLegalDocuments") {
    description = "docs/legal 의 약관 마크다운을 앱 리소스(res/raw)로 복사한다."
    sourceFiles.from(
        rootProject.layout.projectDirectory.dir("docs/legal").asFileTree.matching {
            include("*.md")
        },
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            copyLegalDocuments,
            CopyLegalDocuments::outputDir,
        )
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric 4.17 은 SDK 36 환경을 만들 때(ApplicationSharedMemory) 리플렉션으로
    // jdk.internal.access.SharedSecrets 를 부른다. JDK 17 이상에서는 기본적으로 막혀 있어서
    // "Failed to interact with raw FileDescriptor internals" 로 모든 테스트가 실패한다.
    // public 메서드만 부르므로 add-opens 가 아니라 add-exports 로 그 패키지만 열어 준다.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

roborazzi {
    // 블로그에 쓸 PNG. 저장소에 커밋되는 경로다.
    // 단계별로 폴더를 나누므로 기준 폴더만 지정하고, 테스트에서 "01/..." · "02/..." 로 적는다.
    outputDir = rootProject.layout.projectDirectory.dir("docs/screenshots")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    // Firebase 가 돌려주는 Task 를 코루틴에서 await() 하기 위해 쓴다.
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // 발동 기록(로컬 DB)과 발동 규칙(설정 저장소). 3단계에서 추가.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // 위치 공유와 안심 메시지. 4단계에서 추가.
    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime)

    // 구독 결제. 5단계에서 추가.
    //
    // `com.android.vending.BILLING` 권한과 ProxyBillingActivity 선언은 이 AAR 의
    // 매니페스트에 들어 있어서 병합된다. app/src/main/AndroidManifest.xml 에 적지 않는다.
    implementation(libs.play.billing)
    // 구매 토큰 검증은 서버만 할 수 있다(Play Developer API 는 서비스 계정이 필요하다).
    // 앱은 callable 함수 verifySubscription 을 부르고 결과는 Firestore 에서 읽는다.
    implementation(libs.firebase.functions)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Robolectric 단위 테스트에서 Compose 테스트용 Activity 를 쓰려면 매니페스트에 선언이 필요하다.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

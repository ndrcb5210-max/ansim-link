import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
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
            // 서명 설정은 6단계(출시 준비)에서 환경 변수로 넣는다. 키스토어는 저장소에 두지 않는다.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
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

tasks.withType<Test>().configureEach {
    // Robolectric 4.17 은 SDK 36 환경을 만들 때(ApplicationSharedMemory) 리플렉션으로
    // jdk.internal.access.SharedSecrets 를 부른다. JDK 17 이상에서는 기본적으로 막혀 있어서
    // "Failed to interact with raw FileDescriptor internals" 로 모든 테스트가 실패한다.
    // public 메서드만 부르므로 add-opens 가 아니라 add-exports 로 그 패키지만 열어 준다.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

roborazzi {
    // 블로그에 쓸 PNG. 저장소에 커밋되는 경로다.
    outputDir = rootProject.layout.projectDirectory.dir("docs/screenshots/01")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Robolectric 단위 테스트에서 Compose 테스트용 Activity 를 쓰려면 매니페스트에 선언이 필요하다.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

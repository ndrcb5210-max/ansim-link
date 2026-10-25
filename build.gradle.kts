// 루트 프로젝트에서는 플러그인을 적용하지 않고 버전만 고정한다(각 모듈에서 적용).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
}

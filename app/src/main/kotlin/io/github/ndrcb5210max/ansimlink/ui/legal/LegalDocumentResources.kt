package io.github.ndrcb5210max.ansimlink.ui.legal

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.domain.consent.TermsVersion

/**
 * 약관 문서 하나를 앱에서 보여 주는 데 필요한 것들.
 *
 * raw 리소스는 docs/legal 의 마크다운을 빌드할 때 복사한 것이다
 * (app/build.gradle.kts 의 copyLegalDocuments).
 *
 * 블록 주석 안에 슬래시와 별표가 붙은 글자를 쓰지 않는다. Kotlin 의 블록 주석은 중첩되어서
 * 거기서 주석이 한 겹 더 열리고 파일 끝까지 주석이 되어 버린다.
 */
data class LegalDocumentResource(
    @param:StringRes val titleRes: Int,
    @param:RawRes val rawRes: Int,
    val version: String,
)

fun LegalDocument.resource(): LegalDocumentResource = when (this) {
    LegalDocument.TermsOfService -> LegalDocumentResource(
        titleRes = R.string.legal_terms_title,
        rawRes = R.raw.legal_terms_of_service,
        version = TermsVersion.TERMS_OF_SERVICE,
    )
    LegalDocument.PrivacyPolicy -> LegalDocumentResource(
        titleRes = R.string.legal_privacy_title,
        rawRes = R.raw.legal_privacy_policy,
        version = TermsVersion.PRIVACY_POLICY,
    )
    LegalDocument.LocationTerms -> LegalDocumentResource(
        titleRes = R.string.legal_location_title,
        rawRes = R.raw.legal_location_terms,
        version = TermsVersion.LOCATION_TERMS,
    )
}

/** raw 리소스의 마크다운 원문을 읽는다. 문서가 짧아서 통째로 읽어도 된다. */
fun readLegalDocument(context: Context, @RawRes rawRes: Int): String =
    context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }

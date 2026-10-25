package io.github.ndrcb5210max.ansimlink.domain.consent

/**
 * 약관 버전.
 *
 * 약관 내용을 고치면 여기 값을 올린다. 저장된 동의 기록에 버전을 같이 남기므로
 * 나중에 '어떤 내용에 동의했는지'를 확인할 수 있고, 버전이 올라가면 다시 동의를 받을 수 있다.
 * 값은 문서를 고친 날짜로 적는다(docs/legal/ 의 문서 머리말과 같아야 한다).
 */
object TermsVersion {
    const val TERMS_OF_SERVICE = "2026-10-24"
    const val PRIVACY_POLICY = "2026-10-24"
    const val LOCATION_TERMS = "2026-10-24"

    /** 약관이 아닌 확인 항목(만 14세 이상)에도 기록 형식을 맞추려고 버전을 둔다. */
    const val AGE_CHECK = "1"
}

/**
 * 가입할 때 받아야 하는 동의 항목.
 *
 * 네 항목 모두 필수다. 하나라도 빠지면 가입 버튼이 꺼진다([SignUpConsentState.canSubmit]).
 * '전체 동의'를 눌러도 항목별 체크는 화면에 그대로 보여 준다(CLAUDE.md 원칙 8).
 *
 * @param key Firestore `users/{uid}.consents` 의 필드 이름. 한 번 정하면 바꾸지 않는다.
 * @param version 동의 기록에 같이 남길 약관 버전.
 * @param document 항목에 딸린 약관 문서. 없으면(만 14세 확인) null.
 */
enum class SignUpConsent(
    val key: String,
    val version: String,
    val document: LegalDocument?,
) {
    AgeOver14(
        key = "age_over_14",
        version = TermsVersion.AGE_CHECK,
        document = null,
    ),
    TermsOfService(
        key = "terms_of_service",
        version = TermsVersion.TERMS_OF_SERVICE,
        document = LegalDocument.TermsOfService,
    ),
    PrivacyPolicy(
        key = "privacy_policy",
        version = TermsVersion.PRIVACY_POLICY,
        document = LegalDocument.PrivacyPolicy,
    ),
    LocationTerms(
        key = "location_terms",
        version = TermsVersion.LOCATION_TERMS,
        document = LegalDocument.LocationTerms,
    ),
    ;

    companion object {
        /** 가입에 반드시 필요한 항목. 지금은 전부 필수다. */
        val required: Set<SignUpConsent> = entries.toSet()
    }
}

/** 앱 안에서 보여 주는 약관 문서. 원본은 docs/legal/ 에 있고 res/raw 로 복사해 둔다. */
enum class LegalDocument(val routeKey: String) {
    TermsOfService("terms"),
    PrivacyPolicy("privacy"),
    LocationTerms("location"),
    ;

    companion object {
        fun fromRouteKey(key: String): LegalDocument? = entries.firstOrNull { it.routeKey == key }
    }
}

/**
 * 가입 화면의 동의 체크 상태.
 *
 * 뒤로 가기나 화면 밖 터치로는 이 상태가 바뀌지 않는다. 체크박스를 직접 눌러야만 바뀐다
 * (CLAUDE.md 원칙 8).
 */
data class SignUpConsentState(
    val accepted: Set<SignUpConsent> = emptySet(),
) {
    /** 필수 항목이 모두 체크됐는지. '전체 동의' 체크박스의 상태이기도 하다. */
    val allAccepted: Boolean
        get() = accepted.containsAll(SignUpConsent.required)

    /** 아직 체크하지 않은 필수 항목. 화면에서 무엇이 남았는지 알려 줄 때 쓴다. */
    val missing: List<SignUpConsent>
        get() = SignUpConsent.entries.filterNot { it in accepted }

    /** 가입 버튼을 켤 수 있는지. 이메일·비밀번호 검사와 별개로 동의만 본다. */
    val canSubmit: Boolean
        get() = allAccepted

    fun isAccepted(item: SignUpConsent): Boolean = item in accepted

    fun toggle(item: SignUpConsent): SignUpConsentState =
        copy(accepted = if (item in accepted) accepted - item else accepted + item)

    /** '전체 동의'. 켜면 전부 체크, 끄면 전부 해제한다. */
    fun setAll(value: Boolean): SignUpConsentState =
        copy(accepted = if (value) SignUpConsent.entries.toSet() else emptySet())
}

/** `users/{uid}.consents.{key}` 에 저장할 한 건의 동의 기록. */
data class ConsentRecord(
    val accepted: Boolean,
    val version: String,
    val acceptedAtMillis: Long,
)

/**
 * 체크 상태를 Firestore 에 저장할 모양으로 바꾼다.
 *
 * 필수 항목이 다 체크되지 않았으면 부르지 않는다(가입 버튼이 꺼져 있다).
 */
fun SignUpConsentState.toRecords(nowMillis: Long): Map<String, ConsentRecord> =
    accepted.associate { item ->
        item.key to ConsentRecord(
            accepted = true,
            version = item.version,
            acceptedAtMillis = nowMillis,
        )
    }

package io.github.ndrcb5210max.ansimlink.domain.alert

/**
 * 한 문자 종류의 발동 설정.
 *
 * [enabled] 와 [types] 를 나눈 이유: 스위치를 껐다가 다시 켤 때 고른 유형이 그대로 남아 있어야 한다.
 * 스위치가 꺼져 있으면 [types] 가 무엇이든 발동하지 않는다.
 */
data class SeverityRule(
    val enabled: Boolean,
    val types: Set<DisasterType>,
) {
    fun allows(type: DisasterType): Boolean = enabled && type.ruleType in types

    fun withType(type: DisasterType, on: Boolean): SeverityRule =
        copy(types = if (on) types + type else types - type)
}

/**
 * 발동 규칙 표. 문자 종류(행) × 재난 유형(열).
 *
 * 기본값(CLAUDE.md·3단계 지시문):
 * - 위급재난문자·긴급재난문자: 모든 유형 켬.
 * - 안전안내문자: 끔. 하루에 여러 건 오고 대피가 필요한 내용이 아닌 경우가 많다.
 * - 실종경보문자: 끔. 재난이 아니라 실종자를 찾는 문자라서 내 위치를 공유할 이유가 없다.
 *   사용자가 직접 켤 수 있다.
 *
 * '종류 미상'([AlertSeverity.Unknown])은 표에 없다. 무엇인지 모르는 알림으로는 발동하지 않는다
 * ([TriggerReason.SeverityUnknown]).
 */
data class TriggerRules(
    val bySeverity: Map<AlertSeverity, SeverityRule>,
) {
    fun rule(severity: AlertSeverity): SeverityRule =
        bySeverity[severity] ?: SeverityRule(enabled = false, types = emptySet())

    fun allows(severity: AlertSeverity, type: DisasterType): Boolean =
        rule(severity).allows(type)

    fun withSeverityEnabled(severity: AlertSeverity, enabled: Boolean): TriggerRules =
        copy(bySeverity = bySeverity + (severity to rule(severity).copy(enabled = enabled)))

    fun withType(severity: AlertSeverity, type: DisasterType, on: Boolean): TriggerRules =
        copy(bySeverity = bySeverity + (severity to rule(severity).withType(type, on)))

    companion object {
        /** 설정 화면에 보여 주는 12개 유형 전체. */
        val allTypes: Set<DisasterType> = DisasterType.selectableEntries.toSet()

        val default: TriggerRules = TriggerRules(
            bySeverity = mapOf(
                AlertSeverity.Extreme to SeverityRule(enabled = true, types = allTypes),
                AlertSeverity.Emergency to SeverityRule(enabled = true, types = allTypes),
                AlertSeverity.PublicSafety to SeverityRule(enabled = false, types = allTypes),
                AlertSeverity.AmberAlert to SeverityRule(enabled = false, types = allTypes),
            ),
        )
    }
}

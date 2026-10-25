package io.github.ndrcb5210max.ansimlink.data.alert

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.SeverityRule
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alertPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "alert_rules",
)

/**
 * 발동 규칙(문자 종류 × 재난 유형)을 기기에 저장한다.
 *
 * 저장 모양: 종류마다 켜짐 여부 하나(boolean)와 고른 유형 키 묶음 하나(string set).
 * 저장된 값이 없으면 [TriggerRules.default] 를 쓴다(위급·긴급 켬, 안전안내·실종경보 끔).
 *
 * enum 의 `name` 대신 직접 정한 `key` 를 저장한다. 나중에 Kotlin 쪽 이름을 바꿔도
 * 사용자가 저장해 둔 설정이 깨지지 않는다.
 */
class TriggerRulesStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.alertPreferences)

    val rules: Flow<TriggerRules> = dataStore.data.map(::toRules)

    suspend fun setSeverityEnabled(severity: AlertSeverity, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[enabledKey(severity)] = enabled
            // 아직 유형을 저장한 적이 없으면 기본값을 같이 써 둔다.
            // 그러지 않으면 '스위치는 켰는데 고른 유형이 없는' 상태로 보인다.
            if (preferences[typesKey(severity)] == null) {
                preferences[typesKey(severity)] =
                    TriggerRules.default.rule(severity).types.map { it.key }.toSet()
            }
        }
    }

    suspend fun setTypeEnabled(severity: AlertSeverity, type: DisasterType, on: Boolean) {
        if (!type.selectable) return
        dataStore.edit { preferences ->
            val current = preferences[typesKey(severity)]
                ?: TriggerRules.default.rule(severity).types.map { it.key }.toSet()
            preferences[typesKey(severity)] =
                if (on) current + type.key else current - type.key
        }
    }

    /** 규칙을 기본값으로 되돌린다. */
    suspend fun resetToDefault() {
        dataStore.edit { preferences ->
            AlertSeverity.configurableEntries.forEach { severity ->
                val rule = TriggerRules.default.rule(severity)
                preferences[enabledKey(severity)] = rule.enabled
                preferences[typesKey(severity)] = rule.types.map { it.key }.toSet()
            }
        }
    }

    private fun toRules(preferences: Preferences): TriggerRules = TriggerRules(
        bySeverity = AlertSeverity.configurableEntries.associateWith { severity ->
            val fallback = TriggerRules.default.rule(severity)
            SeverityRule(
                enabled = preferences[enabledKey(severity)] ?: fallback.enabled,
                types = preferences[typesKey(severity)]
                    ?.mapNotNull(DisasterType::fromKey)
                    ?.filter { it.selectable }
                    ?.toSet()
                    ?: fallback.types,
            )
        },
    )

    private fun enabledKey(severity: AlertSeverity) =
        booleanPreferencesKey("rule_enabled_" + severity.key)

    private fun typesKey(severity: AlertSeverity) =
        stringSetPreferencesKey("rule_types_" + severity.key)
}

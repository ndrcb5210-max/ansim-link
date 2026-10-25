package io.github.ndrcb5210max.ansimlink.data.message

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessageSet
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.messagePreferences: DataStore<Preferences> by preferencesDataStore(
    name = "safety_messages",
)

/**
 * 안심 메시지를 기기에 저장한다.
 *
 * 저장 모양: 공통 메시지 하나와 유형마다 한 칸. 유형 키는 [DisasterType.key] 를 쓴다
 * (enum 이름을 쓰면 나중에 Kotlin 쪽 이름을 바꿀 때 사용자 설정이 깨진다 — 3단계와 같다).
 *
 * **서버에 올리지 않는다.** 메시지는 발동할 때 `groups/{gid}/alerts/{id}` 문서에 한 번
 * 실려 가고, 그 문서는 그룹 구성원이 읽는다. 메시지 원고 자체를 미리 서버에 둘 이유가 없다.
 *
 * 유형별 메시지는 **구독을 해지해도 지우지 않는다.** 다시 구독하면 되살아나는 쪽이
 * 사용자에게 낫고, 무료 요금제에서는 [SafetyMessages.select] 가 요금제를 보고 안 쓴다.
 */
class SafetyMessageStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.messagePreferences)

    val messages: Flow<SafetyMessageSet> = dataStore.data.map { preferences ->
        SafetyMessageSet(
            common = preferences[COMMON] ?: SafetyMessages.DEFAULT,
            byType = DisasterType.selectableEntries.mapNotNull { type ->
                preferences[typeKey(type)]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { type to it }
            }.toMap(),
        )
    }

    suspend fun setCommon(raw: String) {
        val normalized = SafetyMessages.normalize(raw)
        dataStore.edit { preferences ->
            // 비우면 저장을 지운다. 그러면 기본 문구가 다시 쓰인다.
            if (normalized.isEmpty()) {
                preferences.remove(COMMON)
            } else {
                preferences[COMMON] = normalized
            }
        }
    }

    /** 재난 유형별 메시지(프리미엄). 빈 값으로 저장하면 '안 적었다'가 되어 공통 문구를 쓴다. */
    suspend fun setForType(type: DisasterType, raw: String) {
        if (!type.selectable) return
        val normalized = SafetyMessages.normalize(raw)
        dataStore.edit { preferences ->
            if (normalized.isEmpty()) {
                preferences.remove(typeKey(type))
            } else {
                preferences[typeKey(type)] = normalized
            }
        }
    }

    /** 공통 메시지를 기본 문구로 되돌린다. */
    suspend fun resetCommon() {
        dataStore.edit { preferences -> preferences.remove(COMMON) }
    }

    private fun typeKey(type: DisasterType) = stringPreferencesKey("message_" + type.key)

    private companion object {
        val COMMON = stringPreferencesKey("message_common")
    }
}

package io.github.ndrcb5210max.ansimlink.data.share

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ndrcb5210max.ansimlink.domain.share.ShareDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sharePreferences: DataStore<Preferences> by preferencesDataStore(
    name = "share_settings",
)

/**
 * 공유 설정.
 *
 * @param shareMinutes 사용자가 고른 공유 시간. 실제로 쓰는 값은
 *   [ShareDuration.effectiveMinutes] 로 요금제 상한을 거친다.
 * @param autoStartEnabled 자동 시작 스위치. **이 값만으로 자동 시작하지 않는다.**
 *   권한까지 같이 보고 정한다([io.github.ndrcb5210max.ansimlink.domain.share.AutoStartFlow]).
 * @param autoStartDisclosureAcceptedAtMillis 명시적 공개 화면에서 '이해했습니다'를 누른 시각.
 *   0 이면 아직 읽지 않았다. 시각까지 남기는 이유: 공개 문구를 고치면 다시 보여 줘야 하는데,
 *   그때 '언제 어떤 문구를 읽었는지' 근거가 필요하다.
 */
data class ShareSettings(
    val shareMinutes: Int = ShareDuration.DEFAULT_MINUTES,
    val autoStartEnabled: Boolean = false,
    val autoStartDisclosureAcceptedAtMillis: Long = 0L,
) {
    val autoStartDisclosureAccepted: Boolean
        get() = autoStartDisclosureAcceptedAtMillis > 0L
}

/**
 * 공유 설정을 기기에 저장한다.
 *
 * 자동 시작은 **끌 때는 묻지 않고 바로 끈다.** 켤 때만 공개 화면과 권한을 거친다.
 * 공유를 줄이는 방향은 언제나 한 번에 되어야 한다(CLAUDE.md 원칙 1·3).
 */
class ShareSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.sharePreferences)

    val settings: Flow<ShareSettings> = dataStore.data.map { preferences ->
        ShareSettings(
            shareMinutes = preferences[SHARE_MINUTES] ?: ShareDuration.DEFAULT_MINUTES,
            autoStartEnabled = preferences[AUTO_START] ?: false,
            autoStartDisclosureAcceptedAtMillis = preferences[DISCLOSURE_AT] ?: 0L,
        )
    }

    suspend fun setShareMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            // 저장할 때도 하드 상한 안으로 넣는다. 요금제 상한은 쓸 때 다시 본다.
            preferences[SHARE_MINUTES] =
                minutes.coerceIn(ShareDuration.MIN_MINUTES, ShareDuration.MAX_MINUTES)
        }
    }

    /** 명시적 공개 화면에서 '이해했습니다'를 눌렀을 때만 부른다. */
    suspend fun acceptAutoStartDisclosure(nowMillis: Long) {
        dataStore.edit { preferences -> preferences[DISCLOSURE_AT] = nowMillis }
    }

    /**
     * 자동 시작 스위치.
     *
     * 켜는 쪽은 공개 화면을 읽었는지 여기서 한 번 더 막는다. 화면 흐름이 바뀌어도
     * 공개 없이 켜지는 일이 없게 하려는 것이다(이중 안전장치).
     */
    suspend fun setAutoStartEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled && (preferences[DISCLOSURE_AT] ?: 0L) <= 0L) return@edit
            preferences[AUTO_START] = enabled
        }
    }

    private companion object {
        val SHARE_MINUTES = intPreferencesKey("share_minutes")
        val AUTO_START = booleanPreferencesKey("auto_start_enabled")
        val DISCLOSURE_AT = longPreferencesKey("auto_start_disclosure_at")
    }
}

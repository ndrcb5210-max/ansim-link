package io.github.ndrcb5210max.ansimlink.ui.share

import androidx.annotation.StringRes
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.share.ShareBlockReason

/**
 * 공유를 시작하지 못한 이유의 화면 문구.
 *
 * 3단계의 [io.github.ndrcb5210max.ansimlink.ui.alert.AlertLabels] 와 같은 자리다.
 * 다만 이 문구는 화면(Compose)뿐 아니라 **서비스와 알림**에서도 쓴다. 그래서
 * `@Composable` 함수가 아니라 문자열 리소스 ID 만 돌려주는 object 로 두었다
 * (서비스에서는 `getString(...)` 으로 읽는다).
 */
object ShareBlockMessages {

    @StringRes
    fun messageRes(reason: ShareBlockReason): Int = when (reason) {
        ShareBlockReason.NotSignedIn -> R.string.share_blocked_not_signed_in
        ShareBlockReason.NoGroup -> R.string.share_blocked_no_group
        ShareBlockReason.ConsentWithdrawn -> R.string.share_blocked_consent_withdrawn
        ShareBlockReason.Paused -> R.string.share_blocked_paused
        ShareBlockReason.LocationPermissionMissing -> R.string.share_blocked_location_permission
        ShareBlockReason.AlreadySharing -> R.string.share_blocked_already_sharing
        ShareBlockReason.BackgroundLocationMissing -> R.string.share_blocked_background_location
    }
}

/** 제공 목적(위치정보법 제19조③의 '목적')을 화면 문구로. */
@StringRes
fun ProvisionPurpose.labelRes(): Int = when (this) {
    ProvisionPurpose.AlertPrompt -> R.string.provision_purpose_alert_prompt
    ProvisionPurpose.AlertAuto -> R.string.provision_purpose_alert_auto
    ProvisionPurpose.ManualShare -> R.string.provision_purpose_manual
}

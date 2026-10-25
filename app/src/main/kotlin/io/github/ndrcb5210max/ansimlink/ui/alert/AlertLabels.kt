package io.github.ndrcb5210max.ansimlink.ui.alert

import androidx.annotation.StringRes
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertConfidence
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason

// 문자 종류(AlertSeverity)와 재난 유형(DisasterType)의 이름은 제도에서 정한 어휘라서
// enum 안에 `label` 로 두었다. 여기에는 사람이 읽는 설명 문구만 모은다.

@StringRes
fun AlertConfidence.labelRes(): Int = when (this) {
    AlertConfidence.High -> R.string.confidence_high
    AlertConfidence.Medium -> R.string.confidence_medium
    AlertConfidence.Low -> R.string.confidence_low
}

@StringRes
fun TriggerReason.labelRes(): Int = when (this) {
    TriggerReason.Triggered -> R.string.reason_triggered
    TriggerReason.SeverityUnknown -> R.string.reason_severity_unknown
    TriggerReason.SeverityDisabled -> R.string.reason_severity_disabled
    TriggerReason.TypeDisabled -> R.string.reason_type_disabled
    TriggerReason.DuplicateBody -> R.string.reason_duplicate_body
    TriggerReason.DuplicateSameKind -> R.string.reason_duplicate_same_kind
}

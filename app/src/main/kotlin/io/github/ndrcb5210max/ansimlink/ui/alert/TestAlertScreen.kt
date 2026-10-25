package io.github.ndrcb5210max.ansimlink.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.service.TestAlertPublisher
import io.github.ndrcb5210max.ansimlink.service.TestAlertSample
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 디버그 빌드 전용 '테스트 재난문자 보내기'.
 *
 * 실제 재난문자를 기다리지 않고 알림 → 리스너 → 분류 → 규칙 → 기록 → 발동 알림까지
 * 한 번에 확인한다. 아래 본문은 모두 실제 문장 형태를 참고해 지어낸 예시다.
 */
@Composable
fun TestAlertScreen(
    samples: List<TestAlertSample>,
    resultMessage: String?,
    onSend: (TestAlertSample) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.debug_test_alert_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.debug_test_alert_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        resultMessage?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        samples.forEach { sample ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = sample.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = sample.body ?: stringResource(R.string.alert_log_no_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onSend(sample) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.debug_test_alert_send, sample.title))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun TestAlertScreenPreview() {
    AnsimLinkTheme {
        TestAlertScreen(
            samples = TestAlertPublisher.samples,
            resultMessage = null,
            onSend = {},
            onBack = {},
        )
    }
}

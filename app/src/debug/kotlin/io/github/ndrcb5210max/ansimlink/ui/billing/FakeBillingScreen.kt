package io.github.ndrcb5210max.ansimlink.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.billing.FakeBillingScenario

/**
 * 디버그 빌드 전용 '가짜 결제' 설정 화면. **릴리스 빌드에는 이 파일이 없다**
 * (`app/src/debug/` 소스셋이다).
 *
 * 고른 값은 앱을 다시 시작한 뒤에 적용된다
 * ([io.github.ndrcb5210max.ansimlink.AppContainer] 가 저장소를 한 번만 만든다).
 * 그 사실을 화면에 적어 둔다 — 적지 않으면 '골랐는데 안 바뀐다'로 시간을 버린다.
 */
@Composable
fun FakeBillingScreen(
    selected: FakeBillingScenario?,
    onSelect: (FakeBillingScenario?) -> Unit,
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
            text = stringResource(R.string.debug_fake_billing_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.debug_fake_billing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 가짜 결제로 어디까지 되는지 분명히 적는다. 프리미엄이 안 열리는 것은 버그가
        // 아니라 설계다(FakeBillingRepository 주석 참고).
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.debug_fake_billing_limit),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        ScenarioRow(
            label = stringResource(R.string.debug_fake_billing_real),
            selected = selected == null,
            onSelect = { onSelect(null) },
        )
        FakeBillingScenario.entries.forEach { scenario ->
            ScenarioRow(
                label = scenario.label,
                selected = selected == scenario,
                onSelect = { onSelect(scenario) },
            )
        }

        Text(
            text = stringResource(
                R.string.debug_fake_billing_current,
                selected?.label ?: stringResource(R.string.debug_fake_billing_real),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScenarioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

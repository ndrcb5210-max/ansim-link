package io.github.ndrcb5210max.ansimlink.ui.share

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 위치 권한을 요청하기 전에 보여 주는 설명 화면(명시적 공개).
 *
 * CLAUDE.md 작업 규칙: 권한을 요청하기 전에 왜 필요한지 먼저 설명한다.
 * 3단계의 [io.github.ndrcb5210max.ansimlink.ui.alert.DetectionSetupScreen] 과 같은 틀이다.
 *
 * 네 가지를 적는다. ① 무엇을 하는지 ② 언제 수집하는지 ③ 얼마나 보관하는지 ④ 누가 보는지.
 * ③·④ 가 특히 중요하다. 위치 공유 앱에서 사용자가 가장 걱정하는 것은
 * '내 이동 경로가 쌓이는가'와 '누가 보는가'다.
 *
 * 버튼은 설명 **아래**에 둔다. 설명을 지나치지 않게 하려는 것이다.
 *
 * 이 화면은 자동 시작의 공개([ShareSettingsScreen] 안의 카드)와 다르다. 여기는
 * '공유 중에만 쓰는 전경 위치'를 설명하고, 거기는 '앱이 닫혀 있을 때도 수집한다'를
 * 설명한다. Google Play 가 둘을 다른 요건으로 본다.
 */
@Composable
fun LocationDisclosureScreen(
    granted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.location_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Section(
            titleRes = R.string.location_disclosure_what_title,
            bodyRes = R.string.location_disclosure_what_body,
        )
        Section(
            titleRes = R.string.location_disclosure_when_title,
            bodyRes = R.string.location_disclosure_when_body,
        )
        Section(
            titleRes = R.string.location_disclosure_how_long_title,
            bodyRes = R.string.location_disclosure_how_long_body,
        )
        Section(
            titleRes = R.string.location_disclosure_who_title,
            bodyRes = R.string.location_disclosure_who_body,
        )

        if (granted) {
            Text(
                text = stringResource(R.string.location_disclosure_granted),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.location_disclosure_allow))
            }
        }
    }
}

@Composable
private fun Section(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun LocationDisclosureScreenPreview() {
    AnsimLinkTheme {
        LocationDisclosureScreen(granted = false, onRequestPermission = {}, onBack = {})
    }
}

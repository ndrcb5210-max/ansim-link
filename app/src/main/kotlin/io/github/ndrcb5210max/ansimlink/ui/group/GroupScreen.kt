package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 그룹 화면. 1단계에서는 빈 상태만 보여 준다.
 *
 * '그룹 만들기'를 누르면 할 일(그룹 생성·초대 코드·동의 화면)은 2단계에서 붙인다.
 */
@Composable
fun GroupScreen(
    modifier: Modifier = Modifier,
    onCreateGroup: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.group_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onCreateGroup) {
            Text(stringResource(R.string.group_create))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupScreenPreview() {
    AnsimLinkTheme {
        GroupScreen()
    }
}

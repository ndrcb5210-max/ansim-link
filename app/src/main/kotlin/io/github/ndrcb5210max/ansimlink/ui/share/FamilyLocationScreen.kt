package io.github.ndrcb5210max.ansimlink.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 가족 위치 보기.
 *
 * **지도 SDK 를 쓰지 않는다.** 지도를 띄우려면 API 키가 필요하고 그 키를 저장소에
 * 커밋할 수 없다(CLAUDE.md 원칙 6). 그래서 구성원별로 받은 시각·정확도·내 위치와의
 * 거리를 글로 보여 주고, [지도 앱으로 열기]로 `geo:` 인텐트를 띄워 기기에 깔린
 * 지도 앱에 넘긴다.
 *
 * 글로 보여 주는 쪽이 오히려 나은 점도 있다. 정확도('약 25m')를 숨기지 않고 적을 수
 * 있어서, 지도의 핀 하나가 주는 '여기 정확히 있다'는 느낌보다 사실에 가깝다.
 *
 * **공유 중인 사람만 보인다.** 공유가 끝나면 목록에서 사라진다. 마지막 위치는 24시간
 * 남아 있지만 그것을 계속 띄우면 '지금 거기 있다'로 잘못 읽힌다.
 */
@Composable
fun FamilyLocationScreen(
    state: FamilyLocationUiState,
    onSelectGroup: (String) -> Unit,
    onOpenMap: (FamilyMemberItem) -> Unit,
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
            text = stringResource(R.string.family_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.family_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 그룹이 여러 개일 때만 고르는 줄을 보여 준다(무료는 1개라 보통 안 보인다).
        if (state.groups.size > 1) {
            Text(
                text = stringResource(R.string.family_group_picker),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.groups.forEach { option ->
                    FilterChip(
                        selected = state.selectedGroupId == option.id,
                        onClick = { onSelectGroup(option.id) },
                        label = { Text(option.name) },
                    )
                }
            }
        }

        when {
            state.loading -> Text(
                text = stringResource(R.string.group_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.members.isEmpty() -> EmptyCard()

            else -> {
                if (state.myLocationUnknown) {
                    Text(
                        text = stringResource(R.string.family_distance_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.members.forEach { member ->
                    MemberCard(member = member, onOpenMap = { onOpenMap(member) })
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.family_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.family_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberCard(
    member: FamilyMemberItem,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = member.displayName, style = MaterialTheme.typography.titleMedium)

            if (!member.hasLocation) {
                // 공유를 막 시작했으면 첫 위치가 아직 안 올라왔을 수 있다.
                Text(
                    text = stringResource(R.string.family_no_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                member.distanceText?.let { distance ->
                    Text(
                        text = stringResource(R.string.family_distance, distance),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                member.updatedText?.let { updated ->
                    Text(
                        text = stringResource(R.string.family_updated, updated),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // 정확도를 숨기지 않는다. '약 25m' 와 '약 400m' 는 전혀 다른 정보다.
                Text(
                    text = member.accuracyText
                        ?.let { stringResource(R.string.family_accuracy, it.toInt()) }
                        ?: stringResource(R.string.family_accuracy_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            member.endsAtText?.let { endsAt ->
                Text(
                    text = stringResource(R.string.family_ends_at, endsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (member.hasLocation) {
                OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.family_open_map))
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun FamilyLocationScreenPreview() {
    AnsimLinkTheme {
        FamilyLocationScreen(
            state = FamilyLocationUiState(
                loading = false,
                members = listOf(
                    FamilyMemberItem(
                        uid = "a",
                        displayName = "지은",
                        updatedText = "18:32",
                        accuracyText = "25",
                        distanceText = "1.2km",
                        endsAtText = "19:05",
                        latitude = 37.5665,
                        longitude = 126.9780,
                    ),
                ),
            ),
            onSelectGroup = {},
            onOpenMap = {},
            onBack = {},
        )
    }
}

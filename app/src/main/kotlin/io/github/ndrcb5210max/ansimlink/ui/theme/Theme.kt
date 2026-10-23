package io.github.ndrcb5210max.ansimlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AnsimBlue40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = AnsimBlue90,
    onPrimaryContainer = AnsimBlue10,
    secondary = AnsimTeal40,
    secondaryContainer = AnsimTeal90,
    onSecondaryContainer = AnsimTeal10,
    tertiary = AnsimGreen40,
    tertiaryContainer = AnsimGreen90,
    onTertiaryContainer = AnsimGreen10,
)

private val DarkColors = darkColorScheme(
    primary = AnsimBlue80,
    onPrimary = AnsimBlue10,
    primaryContainer = AnsimBlue30,
    onPrimaryContainer = AnsimBlue90,
    secondary = AnsimTeal80,
    secondaryContainer = AnsimTeal30,
    onSecondaryContainer = AnsimTeal90,
    tertiary = AnsimGreen80,
    tertiaryContainer = AnsimGreen30,
    onTertiaryContainer = AnsimGreen90,
)

/**
 * 앱 테마.
 *
 * 다이내믹 컬러(Material You)는 쓰지 않는다. 기기 배경색에 따라 색이 바뀌면
 * Roborazzi 스크린샷 테스트 결과가 기기마다 달라져서 비교할 수 없기 때문이다.
 */
@Composable
fun AnsimLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

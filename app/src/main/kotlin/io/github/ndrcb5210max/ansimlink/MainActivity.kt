package io.github.ndrcb5210max.ansimlink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.ndrcb5210max.ansimlink.ui.AnsimLinkApp
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

class MainActivity : ComponentActivity() {

    /**
     * 알림에서 '위치 보기'를 눌러 들어온 경우의 그룹 ID.
     *
     * [onNewIntent] 로도 들어오므로(앱이 이미 떠 있을 때) 상태로 들고 있는다.
     */
    private val openGroupId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AnsimLinkApplication).container
        openGroupId.value = intent?.getStringExtra(EXTRA_OPEN_GROUP_ID)

        setContent {
            AnsimLinkTheme {
                AnsimLinkApp(
                    container = container,
                    openGroupId = openGroupId.value,
                    onOpenGroupHandled = { openGroupId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 앱이 이미 떠 있을 때 알림을 누른 경우. setIntent 로 바꿔 두지 않으면
        // 다음에 getIntent 를 읽는 쪽이 옛 인텐트를 본다.
        setIntent(intent)
        openGroupId.value = intent.getStringExtra(EXTRA_OPEN_GROUP_ID)
    }

    companion object {
        /** 가족 위치 보기 화면을 바로 열 때 넘기는 그룹 ID. */
        const val EXTRA_OPEN_GROUP_ID = "io.github.ndrcb5210max.ansimlink.OPEN_GROUP_ID"
    }
}

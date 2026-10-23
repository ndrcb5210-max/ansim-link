package io.github.ndrcb5210max.ansimlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ndrcb5210max.ansimlink.ui.AnsimLinkApp
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnsimLinkTheme {
                AnsimLinkApp()
            }
        }
    }
}

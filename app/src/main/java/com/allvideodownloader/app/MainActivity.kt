package com.allvideodownloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.allvideodownloader.app.ui.AppRoot
import com.allvideodownloader.app.ui.theme.AllVideoDownloaderTheme

class MainActivity : ComponentActivity() {

    /** Link handed to us by another app via ACTION_SEND, consumed once by the UI. */
    private var incomingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingLink = firstUrlIn(intent)

        setContent {
            AllVideoDownloaderTheme {
                AppRoot(
                    incomingLink = incomingLink,
                    onIncomingLinkConsumed = { incomingLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        firstUrlIn(intent)?.let { incomingLink = it }
    }

    private fun firstUrlIn(intent: Intent?): String? {
        val candidate = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        return URL_PATTERN.find(candidate)?.value
    }

    private companion object {
        val URL_PATTERN = Regex("""https?://\S+""")
    }
}

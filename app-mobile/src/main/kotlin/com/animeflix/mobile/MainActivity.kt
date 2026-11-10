package com.animeflix.mobile

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.animeflix.playback.AccountGate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveCallback(intent)
        setContent { AccountGate(enabled = !BuildConfig.DEMO_CATALOG, allowPreview = BuildConfig.PREVIEW_PROFILES,
            authCallback = callback, onAuthCallbackConsumed = { callback = null }) { AnimeFlixApp() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveCallback(intent) }
    private fun receiveCallback(incoming: Intent) {
        val uri = incoming.data
        if (incoming.action == Intent.ACTION_VIEW && uri?.scheme == packageName && uri.host == "auth-callback") callback = uri.toString()
        incoming.data = null
    }
}

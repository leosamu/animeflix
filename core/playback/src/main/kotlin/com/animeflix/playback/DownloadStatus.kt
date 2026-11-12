package com.animeflix.playback

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeflix.data.AccountManager
import com.animeflix.data.AccountState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private fun AccountState.downloadOwner() = "${accountId}:${selected?.id}"
private data class DownloadSnapshot(val owner: String? = null, val entries: List<OfflineEpisode>? = null)

/** One lifecycle-subscribed poll for all episode buttons, including long lists and player controls. */
private object DownloadStatus {
    private var shared: StateFlow<DownloadSnapshot>? = null
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized fun observe(context: Context): StateFlow<DownloadSnapshot> = shared ?: run {
        val app = context.applicationContext
        val accounts = AccountManager.create(app)
        val store = EpisodeDownloads(app)
        accounts.state.map { it.downloadOwner() }.distinctUntilChanged().flatMapLatest { owner -> flow {
            emit(DownloadSnapshot(owner))
            while(true) {
                val entries = try { store.list() } catch(failure: Exception) {
                    if(failure is CancellationException) throw failure
                    null
                }
                if(accounts.state.value.downloadOwner() == owner) emit(DownloadSnapshot(owner, entries))
                delay(1_000)
            }
        } }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.WhileSubscribed(5_000), DownloadSnapshot())
            .also { shared = it }
    }
}

@Composable
internal fun currentProfileDownloads(): List<OfflineEpisode>? {
    val context = LocalContext.current
    val accounts = remember { AccountManager.create(context) }
    val account by accounts.state.collectAsStateWithLifecycle()
    val snapshot by remember { DownloadStatus.observe(context) }.collectAsStateWithLifecycle()
    return snapshot.entries.takeIf { snapshot.owner == account.downloadOwner() }
}

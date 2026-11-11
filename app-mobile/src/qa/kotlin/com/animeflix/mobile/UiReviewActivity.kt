package com.animeflix.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animeflix.data.AccountState
import com.animeflix.data.ViewingProfile
import com.animeflix.model.Anime
import com.animeflix.model.WatchEntry
import com.animeflix.playback.AnimeFlixColors
import com.animeflix.playback.ProfilePicker
import com.animeflix.playback.CredentialsForm
import androidx.compose.material3.Text

/** Development-only visual review, using fixtures without reading/writing any account or history. */
class UiReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val screen = intent.getStringExtra("screen") ?: "credentials"
        val now = System.currentTimeMillis()
        val anime = Anime("review-1", "One Piece", "", posterUrl = "https://cdn.animeav1.com/covers/197.jpg", sourceUrl = "https://animeav1.com/media/one-piece")
        setContent {
            MaterialTheme(colorScheme = AnimeFlixColors) {
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
                    if (screen == "offline-test") {
                        Column(Modifier.padding(28.dp)) {
                            Text("Comprobación de descargas", style = MaterialTheme.typography.headlineMedium)
                            Text("Descargando y verificando un episodio de prueba. Mantén esta pantalla abierta; después se abrirá el vídeo sin conexión.")
                        }
                    } else if (screen == "credentials") {
                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        var submitted by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxSize().padding(28.dp)) {
                            CredentialsForm(email, password, false, false, { email = it }, { password = it }, { submitted = true })
                            if (submitted) Text("Formulario enviado")
                        }
                    } else if (screen == "profiles") {
                        var selected by remember { mutableStateOf("") }
                        ProfilePicker(AccountState(accountId = "review", email = "Cuenta de demostración", profiles = listOf(
                            ViewingProfile("review-boy", "Niño"), ViewingProfile("review-girl", "Niña"),
                            ViewingProfile("review-long", "Un perfil con un nombre muy largo"))), false, selected.ifEmpty { null },
                            onSelect = { selected = "Perfil de muestra: ${it.name}" }, onCreate = {}, onRefresh = {}, onSignOut = { finish() })
                    } else {
                        var entries by remember { mutableStateOf(if (screen == "empty") emptyList() else listOf(
                            WatchEntry("${anime.sourceUrl}/3", anime.sourceUrl, "3", now, false, 482_000, 1_420_000),
                            WatchEntry("${anime.sourceUrl}/2", anime.sourceUrl, "2", now - 86_400_000, true, 1_390_000, 1_420_000),
                            WatchEntry("${anime.sourceUrl}/1", anime.sourceUrl, "1", now - 172_800_000)
                        )) }
                        HistoryContent(listOf(anime), entries, null, onPlay = {}, onWatched = { selected ->
                            entries = entries.map { if (it.episodeUrl == selected.episodeUrl) it.copy(watched = !it.watched) else it }
                        })
                    }
                }
            }
        }
    }
}

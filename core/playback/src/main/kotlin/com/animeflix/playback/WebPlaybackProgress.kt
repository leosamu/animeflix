package com.animeflix.playback

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.model.WatchEntry
import org.json.JSONObject

/** The only native operation exposed is validated, episode-scoped playback telemetry. */
class WebPlaybackProgress(
    private val view: WebView,
    private var episodeUrl: String,
    private val lookup: (String) -> WatchEntry?,
    private val save: (String, Long, Long) -> Unit
) {
    private data class Binding(val episode: String, val origin: String)
    private val bindings = mutableMapOf<String, Binding>()
    fun navigate(url: String) {
        if (AnimeAv1Episodes.isEpisodeUrl(url) && episodeUrl != url) { episodeUrl = url; bindings.clear() }
    }

    fun install(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        val origins = setOf("https://player.zilla-networks.com", "https://www.mp4upload.com", "https://animeav1.uns.bio")
        WebViewCompat.addWebMessageListener(view, "AnimeFlixProgress", origins) { _, message, origin, _, reply ->
            val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
            if (raw.length > 1024 || origin.toString() !in origins) return@addWebMessageListener
            runCatching {
                val data = JSONObject(raw)
                val nonce = data.getString("nonce")
                if (nonce.length !in 1..128) return@runCatching
                val duration = data.getDouble("duration")
                if (!duration.isFinite() || duration !in 60.0..86400.0) return@runCatching
                if (data.getString("kind") == "ready") {
                    if (bindings.size >= 16 && nonce !in bindings) return@runCatching
                    bindings[nonce] = Binding(episodeUrl, origin.toString())
                    val previous = lookup(episodeUrl)
                    val seconds = (previous?.positionMs ?: 0) / 1000.0
                    // Do not seek into an unrelated video with a different duration.
                    val durationMatches = previous != null && kotlin.math.abs(previous.durationMs / 1000.0 - duration) < 3
                    val seek = if (durationMatches && seconds in 5.0..(duration - 3)) seconds else 0.0
                    reply.postMessage(JSONObject().put("nonce", nonce).put("position", seek).toString())
                } else if (data.getString("kind") == "progress") {
                    val binding = bindings[nonce] ?: return@runCatching
                    if (binding.episode != episodeUrl || binding.origin != origin.toString()) return@runCatching
                    val position = data.getDouble("position")
                    if (position.isFinite() && position in 0.0..duration) save(episodeUrl, (position * 1000).toLong(), (duration * 1000).toLong())
                }
            }
        }
        WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, origins)
        return true
    }

    companion object {
        // Runs inside each allowed document; no access to parent/cross-origin DOM is attempted.
        internal val SCRIPT = """
            (function(){
              if (!window.AnimeFlixProgress) return;
              var videos = new WeakSet(), records = {};
              AnimeFlixProgress.onmessage = function(event) {
                try {
                  var data = JSON.parse(event.data), record = records[data.nonce];
                  if (!record || record.ready) return;
                  record.ready = true;
                  var v = record.video, p = Number(data.position);
                  if (isFinite(p) && p >= 5 && p < v.duration - 3) v.currentTime = p;
                } catch (_) {}
              };
              function attach(v) {
                if (!(v instanceof HTMLVideoElement) || videos.has(v) || !isFinite(v.duration) || v.duration < 60) return;
                videos.add(v);
                var nonce = Date.now().toString(36) + Math.random().toString(36).slice(2);
                var record = {video:v, ready:false, played:false, last:0}; records[nonce] = record;
                function send(kind) {
                  if (!isFinite(v.duration) || v.duration < 60 || v.duration > 86400) return;
                  AnimeFlixProgress.postMessage(JSON.stringify({kind:kind,nonce:nonce,position:v.currentTime,duration:v.duration}));
                }
                function report(force) {
                  if (!record.ready || !record.played || v.seeking || !isFinite(v.currentTime)) return;
                  if (force || Date.now() - record.last >= 5000) { record.last = Date.now(); send('progress'); }
                }
                v.addEventListener('playing', function(){record.played=true; if(!record.ready) send('ready');});
                v.addEventListener('timeupdate', function(){report(false);});
                v.addEventListener('pause', function(){report(true);});
                v.addEventListener('ended', function(){report(true);});
                window.addEventListener('pagehide', function(){report(true);});
                send('ready');
              }
              document.addEventListener('loadedmetadata', function(e){attach(e.target);}, true);
              document.addEventListener('playing', function(e){attach(e.target);}, true);
            })();
        """.trimIndent()
    }
}

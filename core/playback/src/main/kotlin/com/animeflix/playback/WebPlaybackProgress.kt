package com.animeflix.playback

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.model.WatchEntry
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.animeflix.data.SkipInterval

/** The only native operation exposed is validated, episode-scoped playback telemetry. */
class WebPlaybackProgress(
    private val view: WebView,
    private var episodeUrl: String,
    private val lookup: (String) -> WatchEntry?,
    private val save: (String, Long, Long) -> Unit,
    private val onEnded: (String, Long, Long) -> Unit = { _, _, _ -> },
    private val autoPlay: Boolean = false,
    private val scope: CoroutineScope? = null,
    private val onSkip: (SkipInterval?, () -> Unit) -> Unit = { _, _ -> }
) {
    private data class Binding(val episode: String, val origin: String, var intervals: List<SkipInterval> = emptyList(), var savedAt: Long = 0)
    private val bindings = mutableMapOf<String, Binding>()
    private val ended = mutableSetOf<String>()
    private var shown: Pair<String, SkipInterval?>? = null
    fun navigate(url: String) {
        if (AnimeAv1Episodes.isEpisodeUrl(url) && episodeUrl != url) { episodeUrl = url; bindings.clear(); ended.clear(); shown = null; onSkip(null, {}) }
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
                    if (nonce in bindings) return@runCatching
                    val binding = Binding(episodeUrl, origin.toString())
                    bindings[nonce] = binding
                    scope?.launch {
                        val intervals = com.animeflix.data.AniSkipTimes(view.context).load(binding.episode, (duration * 1000).toLong(), false)
                        if (bindings[nonce] === binding && episodeUrl == binding.episode) binding.intervals = intervals
                    }
                    val previous = lookup(episodeUrl)
                    val seconds = (previous?.positionMs ?: 0) / 1000.0
                    // Do not seek into an unrelated video with a different duration.
                    val durationMatches = previous != null && kotlin.math.abs(previous.durationMs / 1000.0 - duration) < 3
                    val seek = if (durationMatches && seconds in 5.0..(duration - 3)) seconds else 0.0
                    reply.postMessage(JSONObject().put("nonce", nonce).put("position", seek).put("autoPlay", autoPlay).toString())
                } else if (data.getString("kind") in setOf("progress", "ended")) {
                    val binding = bindings[nonce] ?: return@runCatching
                    if (binding.episode != episodeUrl || binding.origin != origin.toString()) return@runCatching
                    val position = data.getDouble("position")
                    if (position.isFinite() && position in 0.0..duration) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (data.optBoolean("force") || data.getString("kind") == "ended" || now - binding.savedAt >= 5_000) {
                            binding.savedAt = now
                            save(episodeUrl, (position * 1000).toLong(), (duration * 1000).toLong())
                        }
                        val interval = binding.intervals.firstOrNull { it.active((position * 1000).toLong(), (duration * 1000).toLong()) }
                        val key = nonce to interval
                        if (shown != key) {
                            shown = key
                            onSkip(interval) {
                                if (interval != null && bindings[nonce] === binding && binding.episode == episodeUrl) {
                                    runCatching { reply.postMessage(JSONObject().put("nonce", nonce).put("kind", "seek")
                                        .put("position", interval.target((duration * 1000).toLong()) / 1000.0).toString()) }
                                    shown = null; onSkip(null, {})
                                }
                            }
                        }
                        if (data.getString("kind") == "ended" && com.animeflix.data.playbackReachedEnd((position * 1000).toLong(), (duration * 1000).toLong()) && ended.add(nonce))
                            onEnded(episodeUrl, (position * 1000).toLong(), (duration * 1000).toLong())
                    }
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
                  if (!record) return;
                  if (data.kind === 'seek') {
                    var target = Number(data.position);
                    if (record.ready && isFinite(target) && target >= 0 && target <= record.video.duration - 2) record.video.currentTime = target;
                    return;
                  }
                  if (record.ready) return;
                  record.ready = true;
                  var v = record.video, p = Number(data.position);
                  if (isFinite(p) && p >= 5 && p < v.duration - 3) v.currentTime = p;
                  if (data.autoPlay === true) v.play().catch(function(){});
                } catch (_) {}
              };
              function attach(v) {
                if (!(v instanceof HTMLVideoElement) || videos.has(v) || !isFinite(v.duration) || v.duration < 60) return;
                videos.add(v);
                var nonce = Date.now().toString(36) + Math.random().toString(36).slice(2);
                var record = {video:v, ready:false, played:false, last:0}; records[nonce] = record;
                function send(kind, force) {
                  if (!isFinite(v.duration) || v.duration < 60 || v.duration > 86400) return;
                  AnimeFlixProgress.postMessage(JSON.stringify({kind:kind,nonce:nonce,position:v.currentTime,duration:v.duration,force:force===true}));
                }
                function report(force) {
                  if (!record.ready || !record.played || v.seeking || !isFinite(v.currentTime)) return;
                  if (force || Date.now() - record.last >= 500) { record.last = Date.now(); send('progress', force); }
                }
                v.addEventListener('playing', function(){record.played=true; if(!record.ready) send('ready');});
                v.addEventListener('timeupdate', function(){report(false);});
                v.addEventListener('pause', function(){report(true);});
                v.addEventListener('ended', function(){report(true); if(record.ready && record.played && !v.seeking && v.ended) send('ended');});
                window.addEventListener('pagehide', function(){report(true);});
                send('ready');
              }
              document.addEventListener('loadedmetadata', function(e){attach(e.target);}, true);
              document.addEventListener('playing', function(e){attach(e.target);}, true);
            })();
        """.trimIndent()
    }
}

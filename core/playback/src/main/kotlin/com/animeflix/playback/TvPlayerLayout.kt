package com.animeflix.playback

import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.animeflix.model.PlaybackServer

@Composable
internal fun TvPlayerLayout(title: String, servers: List<PlaybackServer>, loading: Boolean, error: String?,
    watched: Boolean, positionLabel: String?, onMark: () -> Unit, onBack: () -> Unit, onNative: (() -> Unit)? = null,
    player: @Composable (Boolean, Int, Boolean, Int, Boolean, () -> Unit) -> Unit
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var reload by rememberSaveable { mutableIntStateOf(0) }
    var fit by rememberSaveable { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    BackHandler { menu = !menu }
    LaunchedEffect(menu) { if(menu) first.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        player(original, selected, fit, reload, !menu, { menu = true })
        if(menu) TvPlaybackPanel(title, Modifier.align(Alignment.CenterStart)) {
            TvPlaybackButton("Ver vídeo · ocultar controles", { menu = false }, Modifier.focusRequester(first))
            onNative?.let { TvPlaybackButton("Usar reproducción nativa", it) }
            Text("Cruceta: puntero · OK: pulsar · Atrás o Menú: controles", style = MaterialTheme.typography.bodySmall)
            TvPlaybackButton("Recargar reproductor", { reload++; menu = false })
            TvPlaybackButton(if (fit) "Mostrar página completa" else "Ajustar vídeo a la pantalla", { fit = !fit; menu = false })
            positionLabel?.let { Text(it) }
            TvPlaybackButton(if (watched) "Visto · desmarcar" else "Marcar como visto", onMark)
            if(loading) Text("Cargando servidores…")
            error?.let { Text(it) }
            Text("Servidor", style = MaterialTheme.typography.titleMedium)
            TvPlaybackButton("Reproductor de la web" + if (original) " ✓" else "", { original = true; menu = false })
            servers.forEachIndexed { index, server ->
                TvPlaybackButton(server.name + if (!original && index == selected) " ✓" else "",
                    { original = false; selected = index; menu = false })
            }
            TvPlaybackButton("Volver a los episodios", onBack)
        }
    }
}

@Composable
fun TvPlaybackPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().widthIn(max = 420.dp)
        .clip(RoundedCornerShape(20.dp)).background(AnimeFlixColors.surface.copy(alpha = 0.97f))
        .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
fun TvPlaybackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().tvRemoteFocus()) { Text(label) }
}

/** Only resizes the published iframe; preserves its source, parent page and scripts. */
internal fun tvVideoLayoutScript(fit: Boolean): String = """
    (function(){
      if(!window.__animeflixTvLayout){
        var enabled=false;
        function apply(){
          var f=document.querySelector('iframe[title="Episodio Embebido"]') || document.querySelector('iframe[allowfullscreen]');
          if(!f)return;
          if(enabled){
            if(!f.hasAttribute('data-animeflix-style'))f.setAttribute('data-animeflix-style',f.getAttribute('style')||'');
            var styles={position:'fixed',left:'0',top:'0',width:'100vw',height:'100vh',maxWidth:'none',maxHeight:'none',minHeight:'0',margin:'0',border:'0',zIndex:'2147483646',background:'#000'};
            Object.keys(styles).forEach(function(k){f.style[k]=styles[k];});
          }else if(f.hasAttribute('data-animeflix-style')){
            f.setAttribute('style',f.getAttribute('data-animeflix-style'));f.removeAttribute('data-animeflix-style');
          }
        }
        window.__animeflixTvLayout=function(value){enabled=!!value;apply();};
        new MutationObserver(apply).observe(document.documentElement,{childList:true,subtree:true});
      }
      window.__animeflixTvLayout($fit);
    })();
""".trimIndent()

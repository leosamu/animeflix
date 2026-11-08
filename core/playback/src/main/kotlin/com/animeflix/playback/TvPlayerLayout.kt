package com.animeflix.playback

import androidx.activity.compose.BackHandler
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
        if(menu) Column(Modifier.align(Alignment.CenterStart).fillMaxHeight().widthIn(max = 420.dp)
            .background(Color(0xF0181820)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Button(onClick = { menu = false }, modifier = Modifier.fillMaxWidth().focusRequester(first)) { Text("Ver vídeo · ocultar controles") }
            onNative?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Usar reproducción nativa") } }
            Text("Cruceta: puntero · OK: pulsar · Atrás o Menú: controles", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { reload++; menu = false }, modifier = Modifier.fillMaxWidth()) { Text("Recargar reproductor") }
            Button(onClick = { fit = !fit; menu = false }, modifier = Modifier.fillMaxWidth()) {
                Text(if(fit) "Mostrar página completa" else "Ajustar vídeo a la pantalla")
            }
            positionLabel?.let { Text(it) }
            Button(onClick = onMark, modifier = Modifier.fillMaxWidth()) { Text(if(watched) "Visto · desmarcar" else "Marcar como visto") }
            if(loading) Text("Cargando servidores…")
            error?.let { Text(it) }
            Text("Servidor", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { original = true; menu = false }, modifier = Modifier.fillMaxWidth()) { Text("Reproductor de la web" + if(original) " ✓" else "") }
            servers.forEachIndexed { index, server ->
                Button(onClick = { original = false; selected = index; menu = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(server.name + if(!original && index == selected) " ✓" else "")
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver a los episodios") }
        }
    }
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

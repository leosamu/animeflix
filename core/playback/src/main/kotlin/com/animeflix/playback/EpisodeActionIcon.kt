package com.animeflix.playback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
@Composable
fun EpisodeActionIcon(watched: Boolean = false, previous: Boolean = false) {
 val color = if(watched) AnimeFlixColors.primary else MaterialTheme.colorScheme.onSurface
 Canvas(Modifier.size(24.dp)) {
  val s = size.width / 24f
  if(!previous) drawCircle(color,9*s,Offset(12*s,12*s),style=Stroke(1.7f*s))
  else for(i in 0..2) drawLine(color,Offset(3*s,(6+5*i)*s),Offset((12-2*i)*s,(6+5*i)*s),1.7f*s)
  val path=Path().apply { moveTo((if(previous) 10 else 7)*s,12*s); lineTo((if(previous) 14 else 10)*s,16*s); lineTo((if(previous) 22 else 17)*s,8*s) }
  drawPath(path,color,style=Stroke(2*s))
 }
}

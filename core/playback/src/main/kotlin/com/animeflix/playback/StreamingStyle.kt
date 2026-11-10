package com.animeflix.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val AnimeFlixColors = darkColorScheme(
    primary = Color(0xFFFF647C), onPrimary = Color(0xFF330710),
    secondary = Color(0xFF78D9D1), background = Color(0xFF0D101B),
    surface = Color(0xFF151A29), surfaceVariant = Color(0xFF242B40),
    onSurface = Color(0xFFF3F1F7), onSurfaceVariant = Color(0xFFADB2C4),
    outline = Color(0xFF454B60)
)

/** Vector avatars, stable for each profile: no remote image or extra account data required. */
@Composable
fun ProfileAvatar(identity: String, modifier: Modifier = Modifier, paletteIndex: Int? = null) {
    val palettes = listOf(
        listOf(Color(0xFFEE6B82), Color(0xFF9F304B)),
        listOf(Color(0xFF48C7C6), Color(0xFF1F647E)),
        listOf(Color(0xFFABA0FF), Color(0xFF5750A0)),
        listOf(Color(0xFFFFC16C), Color(0xFFB86035)),
        listOf(Color(0xFF6ED5A0), Color(0xFF246A65))
    )
    val index = paletteIndex?.mod(palettes.size) ?: (identity.hashCode().toLong() and 0x7fffffff).rem(palettes.size).toInt()
    val palette = palettes[index]
    Canvas(modifier.clip(RoundedCornerShape(24)).background(Brush.linearGradient(palette))) {
        val w = size.width; val h = size.height
        drawCircle(Color.White.copy(alpha = .09f), w * .55f, Offset(w * .88f, h * .04f))
        drawCircle(Color.Black.copy(alpha = .08f), w * .6f, Offset(w * .04f, h * 1.12f))
        val face = Path().apply {
            moveTo(w * .17f, h * .7f)
            lineTo(w * .23f, h * .24f)
            lineTo(w * .41f, h * .39f)
            quadraticTo(w * .51f, h * .34f, w * .61f, h * .39f)
            lineTo(w * .79f, h * .24f)
            lineTo(w * .85f, h * .7f)
            quadraticTo(w * .5f, h * 1.02f, w * .17f, h * .7f)
            close()
        }
        drawPath(face, Color(0xFF13192D).copy(alpha = .83f))
        drawCircle(Color.White, w * .063f, Offset(w * .38f, h * .57f))
        drawCircle(Color.White, w * .063f, Offset(w * .65f, h * .57f))
        drawCircle(palette[0], w * .025f, Offset(w * .395f, h * .57f))
        drawCircle(palette[0], w * .025f, Offset(w * .665f, h * .57f))
        drawArc(Color.White.copy(alpha = .9f), 18f, 144f, false,
            Offset(w * .4f, h * .62f), Size(w * .24f, h * .15f), style = Stroke(w * .021f, cap = StrokeCap.Round))
    }
}

@Composable
fun PlayMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onPrimary) {
    Canvas(modifier.size(18.dp)) {
        drawPath(Path().apply {
            moveTo(size.width * .24f, size.height * .1f)
            lineTo(size.width * .9f, size.height * .5f)
            lineTo(size.width * .24f, size.height * .9f)
            close()
        }, color)
    }
}

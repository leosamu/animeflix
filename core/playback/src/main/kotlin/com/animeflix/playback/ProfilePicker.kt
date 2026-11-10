package com.animeflix.playback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.animeflix.data.AccountState
import com.animeflix.data.ViewingProfile

@Composable
fun ProfilePicker(account: AccountState, busy: Boolean, error: String?, onSelect: (ViewingProfile) -> Unit,
    onCreate: (String) -> Unit, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF261823), background, background))), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(columns = GridCells.Adaptive(132.dp), modifier = Modifier.widthIn(max = 800.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)) {
                    Text("ANIMEFLIX", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(28.dp))
                    Text("¿Quién está viendo?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    Text("Tus series. Tu ritmo. Tu espacio.", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                    if (account.preview) Text("VISTA PREVIA LOCAL", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 16.dp))
                }
            }
            itemsIndexed(account.profiles, key = { _, profile -> profile.id }) { index, profile ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "profileScale")
                val border by animateColorAsState(if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .1f), label = "profileBorder")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.scale(scale)) {
                    Card(onClick = { onSelect(profile) }, enabled = !busy, interactionSource = interaction,
                        shape = RoundedCornerShape(28.dp), border = BorderStroke(if (focused) 3.dp else 1.dp, border),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Ver como ${profile.name}" }) {
                        ProfileAvatar(profile.id, Modifier.fillMaxSize(), paletteIndex = index)
                    }
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(onClick = { name = ""; adding = true }, enabled = !busy,
                        shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White.copy(alpha = .025f)),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Añadir perfil" }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val tint = MaterialTheme.colorScheme.onSurfaceVariant
                            Canvas(Modifier.size(36.dp)) {
                                drawLine(tint, Offset(size.width * .5f, 0f), Offset(size.width * .5f, size.height), 3.dp.toPx(), StrokeCap.Round)
                                drawLine(tint, Offset(0f, size.height * .5f), Offset(size.width, size.height * .5f), 3.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                    Text("Añadir perfil", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    Text(if (account.preview) "Cada perfil guarda su propia aventura." else "Una cuenta. Historias diferentes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    if (!account.preview) Text(account.email, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!account.preview) TextButton(onClick = onRefresh, enabled = !busy) { Text("Actualizar perfiles") }
                        TextButton(onClick = onSignOut, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Text(if (account.preview) "Salir de la prueba" else "Cerrar sesión")
                        }
                    }
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { if (!busy) adding = false },
        shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Un espacio para ti", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ponle nombre a tu perfil. Tus favoritos y tu progreso se guardarán por separado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Nombre del perfil") }, singleLine = true,
                enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = { onCreate(name); adding = false }, enabled = !busy && name.isNotBlank()) { Text("Crear perfil") } },
        dismissButton = { TextButton(onClick = { adding = false }, enabled = !busy) { Text("Cancelar") } })
}

@Composable
fun ActiveProfileBar(profile: ViewingProfile, preview: Boolean, busy: Boolean, syncing: Boolean,
    onSwitch: () -> Unit, onSync: () -> Unit, avatarIndex: Int = 0) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSwitch, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAvatar(profile.id, Modifier.size(32.dp), paletteIndex = avatarIndex)
                Text(profile.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Cambiar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (preview) Text("Prueba local", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        else TextButton(onClick = onSync, enabled = !syncing, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            if (syncing) CircularProgressIndicator(Modifier.size(14.dp).padding(end = 4.dp), strokeWidth = 1.5.dp)
            Text(if (syncing) "Sincronizando" else "Sincronizar", style = MaterialTheme.typography.labelSmall)
        }
    }
}

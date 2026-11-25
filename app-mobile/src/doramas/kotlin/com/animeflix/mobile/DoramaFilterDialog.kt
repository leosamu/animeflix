package com.animeflix.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.animeflix.data.DoramaFilter
import com.animeflix.data.DoramaFilterOption
import com.animeflix.data.DoramasCatalog
import com.animeflix.playback.tvRemoteFocus
import kotlinx.coroutines.CancellationException

@Composable
internal fun DoramaFilterDialog(
    selected: Map<DoramaFilter, DoramaFilterOption>,
    onDismiss: () -> Unit,
    onApply: (Map<DoramaFilter, DoramaFilterOption>) -> Unit
) {
    var draft by remember { mutableStateOf(selected) }
    var options by remember { mutableStateOf<Map<DoramaFilter, List<DoramaFilterOption>>?>(null) }
    var failure by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(retry) {
        failure = false
        try {
            options = DoramasCatalog.filters()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failure = true
        }
    }
    val choices = options
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros del catálogo") },
        text = {
            when {
                failure -> TextButton(onClick = { retry++ }) { Text("Reintentar filtros") }
                choices == null -> CircularProgressIndicator()
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    DoramaFilter.entries.forEach { filter ->
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true },
                                enabled = choices[filter].orEmpty().isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .then(if (filter == DoramaFilter.Genre) Modifier.focusRequester(first) else Modifier)
                                    .tvRemoteFocus()) {
                                Text("${filter.label}: ${draft[filter]?.label ?: "Cualquiera"} ▾")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                                modifier = Modifier.heightIn(max = 300.dp)) {
                                DropdownMenuItem(text = { Text("Cualquiera") }, onClick = {
                                    draft = draft - filter
                                    expanded = false
                                }, modifier = Modifier.tvRemoteFocus())
                                choices[filter].orEmpty().forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                        draft = draft + (filter to option)
                                        expanded = false
                                    }, modifier = Modifier.tvRemoteFocus())
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { draft = emptyMap(); onApply(draft) }, enabled = selected.isNotEmpty() || draft.isNotEmpty(),
                modifier = Modifier.tvRemoteFocus()) { Text("Limpiar") }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }, modifier = Modifier.tvRemoteFocus()) { Text("Aplicar") } }
    )
    LaunchedEffect(choices) {
        if (!choices?.get(DoramaFilter.Genre).isNullOrEmpty()) {
            withFrameNanos { }
            first.requestFocus()
        }
    }
}

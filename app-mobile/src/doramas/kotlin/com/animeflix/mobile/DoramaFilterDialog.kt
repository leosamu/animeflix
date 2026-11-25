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
                        val candidates = choices[filter].orEmpty()
                        val otherFilters = draft - filter
                        var verified by remember(candidates, otherFilters) { mutableStateOf(emptyList<DoramaFilterOption>()) }
                        var checked by remember(candidates, otherFilters) { mutableIntStateOf(0) }
                        var batches by remember(candidates, otherFilters) { mutableIntStateOf(1) }
                        var verifyRetry by remember(candidates, otherFilters) { mutableIntStateOf(0) }
                        var checkFailed by remember(candidates, otherFilters) { mutableStateOf(false) }
                        val limit = minOf(batches * 12, candidates.size)
                        LaunchedEffect(expanded, limit, otherFilters, verifyRetry) {
                            if (!expanded || !filter.verifyOptions || checked >= limit) return@LaunchedEffect
                            checkFailed = false
                            try {
                                verified = verified + DoramasCatalog.availableFilterOptions(
                                    filter, candidates.subList(checked, limit), otherFilters)
                                checked = limit
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                checkFailed = true
                            }
                        }
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
                                val visible = if (filter.verifyOptions) verified else candidates
                                visible.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                        draft = draft + (filter to option)
                                        expanded = false
                                    }, modifier = Modifier.tvRemoteFocus())
                                }
                                if (filter.verifyOptions) {
                                    when {
                                        checkFailed -> DropdownMenuItem(text = { Text("Reintentar opciones") }, onClick = {
                                            verifyRetry++
                                        }, modifier = Modifier.tvRemoteFocus())
                                        checked < limit -> DropdownMenuItem(text = { Text("Comprobando opciones…") },
                                            enabled = false, onClick = {})
                                        checked < candidates.size -> DropdownMenuItem(text = { Text("Comprobar más opciones") },
                                            onClick = { batches++ }, modifier = Modifier.tvRemoteFocus())
                                        verified.isEmpty() -> DropdownMenuItem(text = { Text("Sin opciones con resultados") },
                                            enabled = false, onClick = {})
                                    }
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

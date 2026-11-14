package com.racegow.camtimer.ui.tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.racegow.camtimer.data.TrackEntity
import com.racegow.camtimer.ui.MainViewModel
import com.racegow.camtimer.ui.components.HelpText
import com.racegow.camtimer.ui.formatDateTime
import com.racegow.camtimer.ui.theme.CardBlack
import com.racegow.camtimer.ui.theme.LapGreen

@Composable
fun TracksScreen(vm: MainViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TrackEntity?>(null) }
    var toRename by remember { mutableStateOf<TrackEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text("Tracks", style = MaterialTheme.typography.headlineSmall)
                    HelpText("Each track keeps its own detection zone and lap history. Tap one to make it active.")
                }
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    active = track.id == settings.activeTrackId,
                    onSelect = { vm.selectTrack(track.id) },
                    onRename = { toRename = track },
                    onDelete = { toDelete = track },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New track") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showCreate) {
        TrackNameDialog(
            title = "New track",
            confirmLabel = "Create",
            initialName = "",
            onConfirm = vm::createTrack,
            onDismiss = { showCreate = false },
        )
    }
    toRename?.let { track ->
        TrackNameDialog(
            title = "Rename track",
            confirmLabel = "Rename",
            initialName = track.name,
            onConfirm = { name -> vm.renameTrack(track.id, name) },
            onDismiss = { toRename = null },
        )
    }
    toDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete track?") },
            text = { Text("“${track.name}” and all its sessions will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrack(track.id)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    active: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = CardBlack),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) LapGreen else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Created ${formatDateTime(track.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename ${track.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${track.name}")
            }
        }
    }
}

@Composable
private fun TrackNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = false
                },
                label = { Text("Name") },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text("Enter a name that is not already used") }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onConfirm(name)) onDismiss() else error = true }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

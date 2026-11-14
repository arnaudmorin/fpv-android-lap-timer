package com.racegow.camtimer.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.racegow.camtimer.core.LapStats
import com.racegow.camtimer.ui.components.HelpText
import com.racegow.camtimer.ui.components.SectionCard
import com.racegow.camtimer.ui.components.StatTile
import com.racegow.camtimer.ui.formatDateTime
import com.racegow.camtimer.ui.formatLap
import com.racegow.camtimer.ui.formatShort
import com.racegow.camtimer.ui.theme.CardBlack
import com.racegow.camtimer.ui.theme.LapGreen
import com.racegow.camtimer.ui.theme.TriggerAmber

@Composable
fun StatsScreen(vm: StatsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SessionSummary?>(null) }
    val track = state.track

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active track", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text(track.name, style = MaterialTheme.typography.headlineSmall)
                HelpText("${state.sessions.size} saved sessions")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Best lap", formatLap(state.bestLapMs), Modifier.weight(1f), valueColor = LapGreen)
                StatTile(
                    "Best 3 consecutive",
                    formatLap(state.best3?.totalMs),
                    Modifier.weight(1f),
                    detail = state.best3?.lapsMs?.joinToString(" · ") { formatShort(it) },
                    valueColor = TriggerAmber,
                )
            }
        }
        if (state.sessions.size >= 2) {
            item {
                // Oldest session on the left.
                val chronological = state.sessions.reversed()
                SectionCard("Progression") {
                    HelpText("One point per session; lower is faster.")
                    LineChart("Best lap", chronological.map { it.session.bestLapMs }, LapGreen)
                    Spacer(Modifier.height(12.dp))
                    LineChart("Best 3 consecutive", chronological.map { it.session.best3TotalMs }, TriggerAmber)
                }
            }
        }
        item {
            Text("Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.sessions.isEmpty()) {
            item { HelpText("No sessions yet. Start the timer, fly some laps, then Stop & save.") }
        }
        items(state.sessions, key = { it.session.id }) { summary ->
            SessionCard(summary, onDelete = { toDelete = summary })
        }
    }

    toDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete session?") },
            text = { Text("Session of ${formatDateTime(summary.session.startedAt)} with ${summary.lapsMs.size} laps.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(summary.session.id)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onDelete: () -> Unit) {
    var expanded by rememberSaveable(summary.session.id) { mutableStateOf(false) }
    val best = summary.session.bestLapMs
    val best3 = LapStats.best3Consecutive(summary.lapsMs)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CardBlack),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDateTime(summary.session.startedAt), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${summary.lapsMs.size} laps · best ${formatLap(best)} · best 3 ${formatLap(best3?.totalMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete session")
                }
            }
            if (expanded) {
                summary.lapsMs.forEachIndexed { i, ms ->
                    val isBest = ms == best
                    val inBest3 = best3 != null && i in best3.startIndex until best3.startIndex + 3
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp, top = 2.dp)
                            .background(if (isBest) LapGreen.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Lap ${i + 1}", modifier = Modifier.weight(1f))
                        if (inBest3) Text("best 3  ", style = MaterialTheme.typography.labelSmall, color = TriggerAmber)
                        Text(
                            formatLap(ms),
                            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                            color = if (isBest) LapGreen else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Minimal line chart: one point per session, scaled between the series' min and max. */
@Composable
private fun LineChart(title: String, values: List<Long?>, color: Color) {
    val points = values.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
            if (points.isNotEmpty()) {
                Text(
                    "${formatLap(points.minOf { it.second })} – ${formatLap(points.maxOf { it.second })}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val axisColor = MaterialTheme.colorScheme.outlineVariant
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = 8.dp),
        ) {
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            if (points.isEmpty()) return@Canvas
            val min = points.minOf { it.second }
            val max = points.maxOf { it.second }
            val span = (max - min).coerceAtLeast(1L).toFloat()
            val pad = 6.dp.toPx()
            fun x(i: Int) = if (values.size == 1) size.width / 2 else pad + i * (size.width - 2 * pad) / (values.size - 1)
            fun y(v: Long) = size.height - pad - (v - min) / span * (size.height - 2 * pad)

            val path = Path()
            points.forEachIndexed { k, (i, v) ->
                if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { (i, v) -> drawCircle(color, 3.5.dp.toPx(), Offset(x(i), y(v))) }
        }
    }
}

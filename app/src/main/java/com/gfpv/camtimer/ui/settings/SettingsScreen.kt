package com.gfpv.camtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gfpv.camtimer.core.AnnounceFormat
import com.gfpv.camtimer.core.Direction
import com.gfpv.camtimer.core.VoiceLanguage
import com.gfpv.camtimer.data.AppSettings
import com.gfpv.camtimer.data.TrackEntity
import com.gfpv.camtimer.data.zone
import com.gfpv.camtimer.tts.VoiceStatus
import com.gfpv.camtimer.ui.MainViewModel
import com.gfpv.camtimer.ui.components.HelpText
import com.gfpv.camtimer.ui.components.LabeledSlider
import com.gfpv.camtimer.ui.components.SectionCard
import com.gfpv.camtimer.ui.components.SegmentedChoice
import com.gfpv.camtimer.ui.components.StepSlider
import com.gfpv.camtimer.ui.components.SwitchRow
import com.gfpv.camtimer.ui.theme.TriggerAmber
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val track by vm.activeTrack.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCameras() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        CameraSection(vm, settings)
        DetectionSection(vm, settings)
        track?.let { ZoneSection(vm, it) }
        VoiceSection(vm, settings)
        SectionCard("Display") {
            SwitchRow(
                "Keep screen on",
                "The screen never sleeps while the app is open",
                settings.keepScreenOn,
            ) { on -> vm.updateSettings { it.copy(keepScreenOn = on) } }
        }
        HelpText("Version ${appVersion()}", Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun CameraSection(vm: MainViewModel, settings: AppSettings) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    SectionCard("Camera") {
        val list = cameras
        when {
            list == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            list.isEmpty() -> HelpText("No camera found.")
            else -> {
                val selected = list.firstOrNull { it.id == settings.cameraId }
                    ?: list.firstOrNull { it.isBack }
                    ?: list.first()
                list.forEach { camera ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = camera.id == selected.id,
                                onClick = { vm.selectCamera(camera.id) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = camera.id == selected.id, onClick = null)
                        Text(camera.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                StepSlider(
                    label = "Frame rate",
                    options = listOf(0) + selected.fpsOptions,
                    selected = settings.fps,
                    format = { if (it == 0) "Auto" else "$it fps" },
                    onSelect = { fps -> vm.updateSettings { it.copy(fps = fps) } },
                )
                HelpText("A fast drone may cross the zone in 100 ms: that is only 3 frames at 30 fps. Use the highest rate that stays smooth.")
            }
        }
    }
}

@Composable
private fun DetectionSection(vm: MainViewModel, settings: AppSettings) {
    SectionCard("Detection") {
        Text("Lap direction", style = MaterialTheme.typography.bodyLarge)
        SegmentedChoice(
            options = Direction.entries,
            selected = settings.direction,
            label = {
                when (it) {
                    Direction.LEFT_TO_RIGHT -> "L → R"
                    Direction.RIGHT_TO_LEFT -> "R → L"
                    Direction.EITHER -> "Both"
                    Direction.ANY_MOTION -> "Any"
                }
            },
            onSelect = { d -> vm.updateSettings { it.copy(direction = d) } },
        )
        HelpText(
            when (settings.direction) {
                Direction.LEFT_TO_RIGHT -> "A lap counts when something crosses the gate line from left to right."
                Direction.RIGHT_TO_LEFT -> "A lap counts when something crosses the gate line from right to left."
                Direction.EITHER -> "A lap counts when something crosses the gate line, whatever the direction."
                Direction.ANY_MOTION -> "Legacy mode: any motion in the zone counts, the gate line is ignored."
            },
        )
        LabeledSlider(
            label = "Sensitivity",
            value = settings.sensitivity,
            valueRange = 1f..30f,
            step = 1f,
            format = { "${it.roundToInt()} %" },
            onValueChange = { v -> vm.updateSettings { it.copy(sensitivity = v) } },
        )
        HelpText("Share of the zone that must change. Lower it if passes are missed, raise it if shadows trigger.")
        LabeledSlider(
            label = "Minimum lap time",
            value = settings.minLapSeconds,
            valueRange = 0.5f..5f,
            step = 0.1f,
            format = { String.format(Locale.getDefault(), "%.1f s", it) },
            onValueChange = { v -> vm.updateSettings { it.copy(minLapSeconds = v) } },
        )
        LabeledSlider(
            label = "Max crossing time",
            value = settings.maxTransitMs.toFloat(),
            valueRange = 200f..1500f,
            step = 50f,
            format = { "${it.roundToInt()} ms" },
            onValueChange = { v -> vm.updateSettings { it.copy(maxTransitMs = v.roundToInt()) } },
            enabled = settings.direction != Direction.ANY_MOTION,
        )
        LabeledSlider(
            label = "End of pass after",
            value = settings.gapFrames.toFloat(),
            valueRange = 1f..6f,
            step = 1f,
            format = { "${it.roundToInt()} still frames" },
            onValueChange = { v -> vm.updateSettings { it.copy(gapFrames = v.roundToInt()) } },
            enabled = settings.direction != Direction.ANY_MOTION,
        )
        SwitchRow(
            "Show detection debug",
            "Activity bars for each half and the motion centre on the preview",
            settings.showDebug,
        ) { on -> vm.updateSettings { it.copy(showDebug = on) } }
    }
}

@Composable
private fun ZoneSection(vm: MainViewModel, track: TrackEntity) {
    val z = track.zone
    val percent = { v: Float -> "${v.roundToInt()} %" }
    SectionCard("Zone · ${track.name}") {
        HelpText("You can also drag the corners, edges and gate line directly on the Timer preview.")
        LabeledSlider("Top margin", z.top * 100, 0f..95f, 1f, percent, { v ->
            vm.saveZone(track.id, z.withTop(v / 100f), track.dividerX)
        })
        LabeledSlider("Bottom margin", (1f - z.bottom) * 100, 0f..95f, 1f, percent, { v ->
            vm.saveZone(track.id, z.withBottom(1f - v / 100f), track.dividerX)
        })
        LabeledSlider("Left margin", z.left * 100, 0f..95f, 1f, percent, { v ->
            vm.saveZone(track.id, z.withLeft(v / 100f), track.dividerX)
        })
        LabeledSlider("Right margin", (1f - z.right) * 100, 0f..95f, 1f, percent, { v ->
            vm.saveZone(track.id, z.withRight(1f - v / 100f), track.dividerX)
        })
        LabeledSlider("Gate line position", track.dividerX * 100, 10f..90f, 1f, percent, { v ->
            vm.saveZone(track.id, z, v / 100f)
        })
    }
}

@Composable
private fun VoiceSection(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val status by vm.voiceStatus.collectAsStateWithLifecycle()
    SectionCard("Voice") {
        SwitchRow("Announce laps", "Spoken by the phone's text-to-speech engine", settings.announce) { on ->
            vm.updateSettings { it.copy(announce = on) }
        }
        Text("Language", style = MaterialTheme.typography.bodyLarge)
        SegmentedChoice(
            options = VoiceLanguage.entries,
            selected = settings.voiceLanguage,
            label = {
                when (it) {
                    VoiceLanguage.FRENCH -> "Français"
                    VoiceLanguage.ENGLISH -> "English"
                    VoiceLanguage.SYSTEM -> "System"
                }
            },
            onSelect = { lang -> vm.updateSettings { it.copy(voiceLanguage = lang) } },
        )
        when (status) {
            VoiceStatus.LANGUAGE_MISSING -> {
                Text(
                    "This voice is not installed in your text-to-speech engine; the system language is used meanwhile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TriggerAmber,
                )
                OutlinedButton(onClick = { vm.openInstallVoiceData(context) }) { Text("Install voice data") }
            }
            VoiceStatus.UNAVAILABLE -> Text(
                "No text-to-speech engine is available on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = TriggerAmber,
            )
            else -> Unit
        }
        Text("Announcement", style = MaterialTheme.typography.bodyLarge)
        SegmentedChoice(
            options = AnnounceFormat.entries,
            selected = settings.announceFormat,
            label = {
                when (it) {
                    AnnounceFormat.LAP_AND_TIME -> "Lap + time"
                    AnnounceFormat.TIME_ONLY -> "Time only"
                }
            },
            onSelect = { f -> vm.updateSettings { it.copy(announceFormat = f) } },
        )
        LabeledSlider(
            label = "Speech rate",
            value = settings.speechRate,
            valueRange = 0.5f..2f,
            step = 0.1f,
            format = { String.format(Locale.getDefault(), "%.1f×", it) },
            onValueChange = { v -> vm.updateSettings { it.copy(speechRate = v) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::testVoice) { Text("Test voice") }
            OutlinedButton(onClick = { vm.openTtsSettings(context) }) { Text("TTS settings") }
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "?"
}

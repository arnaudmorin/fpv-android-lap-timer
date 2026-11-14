package com.racegow.camtimer.ui.timer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.racegow.camtimer.core.Direction
import com.racegow.camtimer.core.LapStats
import com.racegow.camtimer.data.AppSettings
import com.racegow.camtimer.data.TrackEntity
import com.racegow.camtimer.data.zone
import com.racegow.camtimer.ui.MainViewModel
import com.racegow.camtimer.ui.components.StatTile
import com.racegow.camtimer.ui.components.ZoneOverlay
import com.racegow.camtimer.ui.formatLap
import com.racegow.camtimer.ui.formatShort
import com.racegow.camtimer.ui.theme.LapGreen
import com.racegow.camtimer.ui.theme.StopRed
import com.racegow.camtimer.ui.theme.TriggerAmber
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(vm: TimerViewModel, mainVm: MainViewModel, onOpenTracks: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val track by mainVm.activeTrack.collectAsStateWithLifecycle()
    val settings by mainVm.settings.collectAsStateWithLifecycle()

    StopWhenBackgrounded(vm)

    CameraPermissionGate {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(8.dp)) {
                    Box(Modifier.weight(1.3f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CameraWithZone(vm, mainVm, track, settings, state.running, fitHeight = true)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 8.dp),
                    ) {
                        TimerControls(vm, state, track, onOpenTracks)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    CameraWithZone(vm, mainVm, track, settings, state.running, fitHeight = false)
                    Spacer(Modifier.height(12.dp))
                    TimerControls(vm, state, track, onOpenTracks)
                }
            }
        }
    }
}

@Composable
private fun CameraWithZone(
    vm: TimerViewModel,
    mainVm: MainViewModel,
    track: TrackEntity?,
    settings: AppSettings,
    running: Boolean,
    fitHeight: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val aspect by vm.frameAspect.collectAsStateWithLifecycle()
    val motion by vm.motion.collectAsStateWithLifecycle()
    val flash by vm.flash.collectAsStateWithLifecycle()

    // FIT_CENTER inside a box of the analysis aspect ratio: the preview fills the box exactly,
    // so normalized zone coordinates line up with the analysed frame.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    LaunchedEffect(settings.cameraId, settings.fps, lifecycleOwner) {
        vm.bindCamera(lifecycleOwner, previewView.surfaceProvider, displayRotation(context))
    }
    DisposableEffect(Unit) {
        onDispose { vm.unbindCamera() }
    }

    Box(
        Modifier
            .then(if (fitHeight) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
            .aspectRatio(aspect, matchHeightConstraintsFirst = fitHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (track != null) {
            ZoneOverlay(
                zone = track.zone,
                divider = track.dividerX,
                direction = settings.direction,
                editable = !running,
                flash = flash,
                motion = if (settings.showDebug) motion else null,
                sensitivity = settings.sensitivity,
                onZoneChange = vm::setZone,
                onZoneCommit = { z, d -> mainVm.saveZone(track.id, z, d) },
                onDirectionTap = { mainVm.updateSettings { it.copy(direction = it.direction.next()) } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ColumnScope.TimerControls(
    vm: TimerViewModel,
    state: TimerUiState,
    track: TrackEntity?,
    onOpenTracks: () -> Unit,
) {
    if (track == null) {
        Text("No track selected.", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onOpenTracks) { Text("Create or select a track") }
        return
    }

    Text(track.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        when {
            state.running && !state.started -> "Running: waiting for the first gate pass…"
            state.running -> "Running: lap ${state.lapsMs.size + 1}"
            else -> "Drag the zone over the gate, tap the arrow to change the lap direction, then start. " +
                "Test passes flash blue, rejected ones red."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.running) LapGreen else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = vm::start,
            enabled = !state.running,
            colors = ButtonDefaults.buttonColors(containerColor = LapGreen, contentColor = Color.Black),
            modifier = Modifier.weight(1f),
        ) { Text("Start") }
        Button(
            onClick = { vm.stop() },
            enabled = state.running,
            colors = ButtonDefaults.buttonColors(containerColor = StopRed, contentColor = Color.Black),
            modifier = Modifier.weight(1f),
        ) { Text("Stop & save") }
        OutlinedButton(
            onClick = vm::clear,
            enabled = !state.running && state.lapsMs.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { Text("Clear") }
    }
    Spacer(Modifier.height(12.dp))

    val ticking = state.running && state.started
    val now = rememberRealtime(ticking)
    val laps = state.lapsMs
    val best = LapStats.bestLap(laps)
    val best3 = LapStats.best3Consecutive(laps)
    val total = if (ticking) now - state.startedAtRealtime else laps.sum()
    val current = if (ticking) now - state.lapStartedAtRealtime else null

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Laps", laps.size.toString(), Modifier.weight(1f))
        StatTile("Current", formatLap(current), Modifier.weight(1f), valueColor = TriggerAmber)
        StatTile("Total", formatLap(total), Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Last", formatLap(laps.lastOrNull()), Modifier.weight(1f))
        StatTile("Best", formatLap(best), Modifier.weight(1f), valueColor = LapGreen)
        StatTile(
            "Best 3",
            formatLap(best3?.totalMs),
            Modifier.weight(1f),
            detail = best3?.lapsMs?.joinToString(" · ") { formatShort(it) },
        )
    }
    Spacer(Modifier.height(12.dp))

    if (laps.isNotEmpty()) {
        Text("Laps", style = MaterialTheme.typography.titleMedium)
        laps.withIndex().reversed().forEach { (i, ms) ->
            val isBest = ms == best
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isBest) LapGreen.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Lap ${i + 1}", modifier = Modifier.weight(1f))
                Text(
                    formatLap(ms),
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) LapGreen else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Order used when tapping the direction badge on the preview. */
private fun Direction.next(): Direction = Direction.entries[(ordinal + 1) % Direction.entries.size]

/** Live clock for the running lap and total time. */
@Composable
private fun rememberRealtime(active: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active) {
        while (active) {
            now = SystemClock.elapsedRealtime()
            delay(50)
        }
    }
    return now
}

/** Stops and saves a running session when the app leaves the screen (but not on rotation). */
@Composable
private fun StopWhenBackgrounded(vm: TimerViewModel) {
    val owner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(owner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                vm.stop(backgrounded = true)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA)
    }
    // Re-check when coming back from the system app settings.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose { }
    }

    if (granted) {
        content()
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "The camera watches the gate to detect the drone passing through it.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open app settings") }
        }
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

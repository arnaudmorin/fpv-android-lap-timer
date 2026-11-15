package com.gfpv.camtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gfpv.camtimer.ui.components.KeepScreenOn
import com.gfpv.camtimer.ui.settings.SettingsScreen
import com.gfpv.camtimer.ui.stats.StatsScreen
import com.gfpv.camtimer.ui.stats.StatsViewModel
import com.gfpv.camtimer.ui.timer.TimerScreen
import com.gfpv.camtimer.ui.timer.TimerViewModel
import com.gfpv.camtimer.ui.tracks.TracksScreen

enum class Tab(val label: String, val icon: ImageVector) {
    TIMER("Timer", Icons.Filled.PlayArrow),
    TRACKS("Tracks", Icons.Filled.Place),
    STATS("Stats", Icons.Filled.Star),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun MainScaffold(factory: ViewModelProvider.Factory) {
    val mainVm: MainViewModel = viewModel(factory = factory)
    val timerVm: TimerViewModel = viewModel(factory = factory)
    val statsVm: StatsViewModel = viewModel(factory = factory)
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val timerState by timerVm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TIMER) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(timerVm) {
        timerVm.messages.collect { snackbar.showSnackbar(it) }
    }
    KeepScreenOn(settings.keepScreenOn)

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        // The camera only runs on the Timer tab: stay there during a session.
                        enabled = !timerState.running || t == Tab.TIMER,
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                Tab.TIMER -> TimerScreen(timerVm, mainVm, onOpenTracks = { tab = Tab.TRACKS })
                Tab.TRACKS -> TracksScreen(mainVm)
                Tab.STATS -> StatsScreen(statsVm)
                Tab.SETTINGS -> SettingsScreen(mainVm)
            }
        }
    }
}

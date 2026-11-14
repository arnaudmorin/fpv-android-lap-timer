package com.racegow.camtimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.racegow.camtimer.ui.MainScaffold
import com.racegow.camtimer.ui.MainViewModel
import com.racegow.camtimer.ui.stats.StatsViewModel
import com.racegow.camtimer.ui.theme.CamTimerTheme
import com.racegow.camtimer.ui.timer.TimerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Black system bars to match the true-black theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        val container = (application as CamTimerApp).container
        val factory = viewModelFactory {
            initializer { MainViewModel(container) }
            initializer { TimerViewModel(container) }
            initializer { StatsViewModel(container) }
        }
        setContent {
            CamTimerTheme {
                MainScaffold(factory)
            }
        }
    }
}

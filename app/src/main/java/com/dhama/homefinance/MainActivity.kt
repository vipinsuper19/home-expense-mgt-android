package com.dhama.homefinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.dhama.homefinance.util.DateTicker
import javax.inject.Inject
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dhama.homefinance.data.preferences.UserPreferences
import com.dhama.homefinance.ui.MainScreen
import com.dhama.homefinance.ui.theme.HomeFinanceTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var dateTicker: DateTicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(dateTicker.resumeObserver)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                UserPreferences.THEME_LIGHT -> false
                UserPreferences.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            HomeFinanceTheme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }
}


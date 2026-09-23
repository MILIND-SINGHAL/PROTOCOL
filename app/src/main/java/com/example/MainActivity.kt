package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BaselineScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PaywallScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.ProtocolAppTheme
import com.example.ui.theme.ProtocolTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ProtocolViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ProtocolViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userProfile by viewModel.userProfile.collectAsState()
            val appNavState by viewModel.appNavState.collectAsState()

            val themeMode = when (userProfile?.themeMode?.lowercase()) {
                "light" -> ThemeMode.LIGHT
                "cozy" -> ThemeMode.COZY
                else -> ThemeMode.DARK
            }

            ProtocolAppTheme(mode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ProtocolTheme.palette.background
                ) {
                    AnimatedContent(
                        targetState = appNavState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "app_navigation"
                    ) { state ->
                        when (state) {
                            "loading", "splash" -> {
                                SplashScreen(
                                    onFinished = {
                                        viewModel.resolveStartupDestination()
                                    }
                                )
                            }
                            "auth" -> {
                                AuthScreen(
                                    viewModel = viewModel,
                                    onAuthSuccess = {
                                        viewModel.resolveStartupDestination()
                                    },
                                    onSkipGuest = {
                                        viewModel.continueAsGuest()
                                    }
                                )
                            }
                            "baseline" -> {
                                BaselineScreen(
                                    onComplete = { wakeTime, focus, wearable ->
                                        viewModel.saveBaseline(wakeTime, focus, wearable)
                                    }
                                )
                            }
                            "paywall" -> {
                                PaywallScreen(
                                    viewModel = viewModel,
                                    onSubscribed = {
                                        viewModel.setNavDestination("dashboard")
                                    },
                                    onDismiss = {
                                        viewModel.setNavDestination("dashboard")
                                    }
                                )
                            }
                            "dashboard" -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    activeThemeMode = themeMode,
                                    onThemeChanged = { newTheme ->
                                        viewModel.setTheme(newTheme)
                                    },
                                    onResetBaseline = {
                                        viewModel.setNavDestination("baseline")
                                    },
                                    onOpenAuth = {
                                        viewModel.setNavDestination("auth")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

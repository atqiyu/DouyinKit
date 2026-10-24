package com.atqiyu.douyinkit

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.ThemeMode
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.parser.ParseActivity
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.parser.ParseSessionStore
import com.atqiyu.douyinkit.ui.DisclaimerDialog
import com.atqiyu.douyinkit.ui.MainScreen
import com.atqiyu.douyinkit.ui.result.ResultScreen
import com.atqiyu.douyinkit.ui.theme.DouyinKitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val incomingSharedText = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        incomingSharedText.value = readSharedText(intent)

        setContent {
            MainContent(sharedTextFlow = incomingSharedText)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingSharedText.value = readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?): String {
        if (intent == null) return ""
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
    }
}

@Composable
private fun MainContent(sharedTextFlow: MutableStateFlow<String>) {
    val sharedText by sharedTextFlow.collectAsStateWithLifecycle()
    val latestResult by ParseSessionStore.lastResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = context.applicationContext.appContainer
    val loadedSettings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: AppSettings()
    val settingsLoaded = loadedSettings != null
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    var visibleResult by remember { mutableStateOf<ParseBatchResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(latestResult) {
        val result = latestResult ?: return@LaunchedEffect
        visibleResult = result
        ParseSessionStore.clear()
    }

    LaunchedEffect(settingsLoaded, settings.historyRetentionDays) {
        if (!settingsLoaded) return@LaunchedEffect
        container.historyRepository.cleanup(settings.historyRetentionDays)
    }

    val parseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // ParseActivity publishes its result through ParseSessionStore.
    }

    DouyinKitTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.dynamicColor,
    ) {
        if (settingsLoaded && !settings.onboardingAccepted) {
            DisclaimerDialog(
                onAccept = {
                    scope.launch {
                        container.settingsRepository.setOnboardingAccepted(true)
                    }
                },
                onExit = { (context as? Activity)?.finish() },
            )
        }
        val result = visibleResult
        if (result != null) {
            BackHandler { visibleResult = null }
            ResultScreen(
                result = result,
                onBack = { visibleResult = null },
            )
        } else {
            MainScreen(
                initialText = sharedText,
                onParse = { input ->
                    parseLauncher.launch(
                        ParseActivity.intent(
                            context = context,
                            inputs = listOf(input),
                        ),
                    )
                },
            )
        }
    }
}

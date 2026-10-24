package com.atqiyu.douyinkit

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.parser.ParseActivity
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.parser.ParseSessionStore
import com.atqiyu.douyinkit.ui.home.HomeScreen
import com.atqiyu.douyinkit.ui.result.ResultScreen
import com.atqiyu.douyinkit.ui.theme.DouyinKitTheme
import kotlinx.coroutines.flow.MutableStateFlow

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
    var visibleResult by remember { mutableStateOf<ParseBatchResult?>(null) }

    LaunchedEffect(latestResult) {
        val result = latestResult ?: return@LaunchedEffect
        visibleResult = result
        ParseSessionStore.clear()
    }

    val parseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // ParseActivity publishes its result through ParseSessionStore.
    }

    DouyinKitTheme {
        val result = visibleResult
        if (result != null) {
            BackHandler { visibleResult = null }
            ResultScreen(
                result = result,
                onBack = { visibleResult = null },
            )
        } else {
            HomeScreen(
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

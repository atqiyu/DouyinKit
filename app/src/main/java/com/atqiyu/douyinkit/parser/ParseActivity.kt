package com.atqiyu.douyinkit.parser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.atqiyu.douyinkit.MainActivity
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.ThemeMode
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.ui.parse.ParseScreen
import com.atqiyu.douyinkit.ui.theme.DouyinKitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ParseActivity : ComponentActivity() {

    private lateinit var engine: DouyinWebViewEngine
    private lateinit var controller: ParseController
    private var inputs: List<String> = emptyList()
    private var parseInputs: List<String> = emptyList()
    private var forceRefresh: Boolean = false
    private var openResultInApp: Boolean = false
    private var published = false
    private val handledOutcomes = mutableSetOf<String>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        inputs = intent.getStringArrayListExtra(EXTRA_INPUTS).orEmpty()
        parseInputs = expandInputs(inputs)
        forceRefresh = intent.getBooleanExtra(EXTRA_FORCE_REFRESH, false)
        openResultInApp = intent.getBooleanExtra(EXTRA_OPEN_RESULT_IN_APP, false)

        val container = applicationContext.appContainer
        engine = DouyinWebViewEngine(this)
        controller = ParseController(
            engine = engine,
            resolver = container.newShortLinkResolver(),
            normalizer = container.newNormalizer(),
            scope = lifecycleScope,
        )

        onBackPressedDispatcher.addCallback(this) {
            controller.cancel()
            setResult(RESULT_CANCELED)
            finish()
        }

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            val loadedSettings by applicationContext.appContainer.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val settings = loadedSettings ?: AppSettings()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            DouyinKitTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                ParseScreen(
                    state = state,
                    engine = engine,
                    onCancel = {
                        controller.cancel()
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onShowWebView = controller::showWebView,
                    onHideWebView = controller::hideWebView,
                    onReload = controller::reloadPage,
                    onRetry = { controller.start(expandInputs(inputs), forceRefresh) },
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            parseInputs.forEach { input ->
                runCatching {
                    applicationContext.appContainer.shareLinkRepository.markProcessing(input)
                }
            }
        }

        lifecycleScope.launch {
            delay(250)
            controller.start(parseInputs, forceRefresh)
        }

        lifecycleScope.launch {
            controller.state.collect { state ->
                val newOutcomes = state.outcomes.filter { it.input !in handledOutcomes }
                if (newOutcomes.isNotEmpty()) {
                    handledOutcomes += newOutcomes.map { it.input }
                    launch(Dispatchers.IO) {
                        newOutcomes.forEach { outcome ->
                            runCatching {
                                if (outcome.success) {
                                    applicationContext.appContainer.shareLinkRepository.markCompleted(
                                        url = outcome.input,
                                        awemeId = outcome.awemeId,
                                        title = outcome.title,
                                        message = "解析完成",
                                    )
                                } else {
                                    applicationContext.appContainer.shareLinkRepository.markFailed(
                                        outcome.input,
                                        outcome.message,
                                    )
                                }
                            }
                        }
                    }
                }
                val finishedSuccessfully = !state.running &&
                    state.stage == ParseStage.DONE &&
                    state.results.isNotEmpty()
                if (finishedSuccessfully && !published) {
                    published = true
                    try {
                        applicationContext.appContainer.historyRepository.saveAll(state.results)
                    } catch (_: Exception) {
                        // History persistence must never block showing a successfully parsed result.
                    }
                    ParseSessionStore.publish(
                        ParseBatchResult(
                            results = state.results,
                            failures = state.failures,
                        ),
                    )
                    if (openResultInApp) {
                        runCatching {
                            startActivity(
                                Intent(this@ParseActivity, MainActivity::class.java).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                                    )
                                },
                            )
                        }
                    }
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        val remaining = parseInputs.filter { it !in handledOutcomes }
        if (remaining.isNotEmpty()) {
            cleanupScope.launch {
                remaining.forEach { input ->
                    runCatching {
                        applicationContext.appContainer.shareLinkRepository.markFailed(
                            input,
                            "已取消或未完成",
                        )
                    }
                }
            }
        }
        controller.destroy()
        super.onDestroy()
    }

    private fun expandInputs(raw: List<String>): List<String> {
        val links = raw.flatMap { input ->
            LinkExtractor.extract(input).map { it.url }
        }.distinct()
        return if (links.isEmpty()) raw else links
    }

    companion object {
        const val EXTRA_INPUTS = "extra_inputs"
        const val EXTRA_FORCE_REFRESH = "extra_force_refresh"
        const val EXTRA_OPEN_RESULT_IN_APP = "extra_open_result_in_app"

        fun intent(
            context: Context,
            inputs: List<String>,
            forceRefresh: Boolean = false,
            openResultInApp: Boolean = false,
        ): Intent {
            return Intent(context, ParseActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_INPUTS, ArrayList(inputs))
                putExtra(EXTRA_FORCE_REFRESH, forceRefresh)
                putExtra(EXTRA_OPEN_RESULT_IN_APP, openResultInApp)
            }
        }
    }
}

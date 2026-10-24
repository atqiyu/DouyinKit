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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.atqiyu.douyinkit.core.net.HttpClients
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.ui.parse.ParseScreen
import com.atqiyu.douyinkit.ui.theme.DouyinKitTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ParseActivity : ComponentActivity() {

    private lateinit var engine: DouyinWebViewEngine
    private lateinit var controller: ParseController
    private var inputs: List<String> = emptyList()
    private var forceRefresh: Boolean = false
    private var published = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        inputs = intent.getStringArrayListExtra(EXTRA_INPUTS).orEmpty()
        forceRefresh = intent.getBooleanExtra(EXTRA_FORCE_REFRESH, false)

        engine = DouyinWebViewEngine(this)
        controller = ParseController(
            engine = engine,
            resolver = ShortLinkResolver(HttpClients.shortLink()),
            normalizer = DouyinDetailNormalizer(),
            scope = lifecycleScope,
        )

        onBackPressedDispatcher.addCallback(this) {
            controller.cancel()
            setResult(RESULT_CANCELED)
            finish()
        }

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            DouyinKitTheme {
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

        lifecycleScope.launch {
            delay(250)
            controller.start(expandInputs(inputs), forceRefresh)
        }

        lifecycleScope.launch {
            controller.state.collect { state ->
                val finishedSuccessfully = !state.running &&
                    state.stage == ParseStage.DONE &&
                    state.results.isNotEmpty()
                if (finishedSuccessfully && !published) {
                    published = true
                    ParseSessionStore.publish(
                        ParseBatchResult(
                            results = state.results,
                            failures = state.failures,
                        ),
                    )
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
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

        fun intent(
            context: Context,
            inputs: List<String>,
            forceRefresh: Boolean = false,
        ): Intent {
            return Intent(context, ParseActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_INPUTS, ArrayList(inputs))
                putExtra(EXTRA_FORCE_REFRESH, forceRefresh)
            }
        }
    }
}

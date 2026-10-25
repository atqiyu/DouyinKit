package com.atqiyu.douyinkit.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.util.LinkExtractor
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.ui.downloads.DownloadsScreen
import com.atqiyu.douyinkit.ui.home.HomeScreen
import com.atqiyu.douyinkit.ui.library.LibraryScreen
import com.atqiyu.douyinkit.ui.result.ResultScreen
import com.atqiyu.douyinkit.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Default.Home),
    LIBRARY("资料库", Icons.Default.PhotoLibrary),
    DOWNLOADS("下载", Icons.Default.Download),
    SETTINGS("设置", Icons.Default.Settings),
}

private const val TAB_FADE_MS = 180

@Composable
fun MainScreen(
    initialText: String = "",
    onParse: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings by context.applicationContext.appContainer.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentTab = MainTab.entries[tabIndex]
    val visitedTabs = remember { mutableStateListOf(MainTab.entries[tabIndex]) }
    var outgoingTab by remember { mutableStateOf<MainTab?>(null) }
    val tabFade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedAweme by remember { mutableStateOf<Aweme?>(null) }
    var lastOpenedAweme by remember { mutableStateOf<Aweme?>(null) }
    LaunchedEffect(selectedAweme) {
        selectedAweme?.let { lastOpenedAweme = it }
    }
    val detailVisible = selectedAweme != null

    var clipboardLink by rememberSaveable { mutableStateOf<String?>(null) }
    var lastClipboardSignature by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(appSettings.clipboardDetection) {
        if (!appSettings.clipboardDetection) {
            clipboardLink = null
            lastClipboardSignature = Long.MIN_VALUE
        }
    }

    DisposableEffect(lifecycleOwner, appSettings.clipboardDetection) {
        if (!appSettings.clipboardDetection) {
            return@DisposableEffect onDispose { }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        var registered = false

        fun inspectClipboard() {
            runCatching {
                if (!clipboard.hasPrimaryClip()) return@runCatching
                val clip = clipboard.primaryClip ?: return@runCatching
                if (clip.itemCount <= 0) return@runCatching
                val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) return@runCatching
                val stamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    clipboard.primaryClipDescription?.timestamp ?: 0L
                } else {
                    0L
                }
                val signature = if (stamp > 0L) stamp else text.hashCode().toLong()
                if (signature == lastClipboardSignature) return@runCatching
                lastClipboardSignature = signature
                clipboardLink = LinkExtractor.extractFirst(text)?.url
            }
        }

        val inspectRunnable = Runnable { inspectClipboard() }
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                inspectClipboard()
            }
        }

        fun register() {
            if (!registered) {
                clipboard.addPrimaryClipChangedListener(listener)
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                clipboard.removePrimaryClipChangedListener(listener)
                registered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()

                Lifecycle.Event.ON_RESUME -> {
                    register()
                    handler.removeCallbacks(inspectRunnable)
                    handler.postDelayed(inspectRunnable, 200)
                }

                Lifecycle.Event.ON_STOP -> {
                    unregister()
                    handler.removeCallbacks(inspectRunnable)
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            register()
            handler.postDelayed(inspectRunnable, 200)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
            handler.removeCallbacks(inspectRunnable)
        }
    }

    fun selectTab(target: MainTab) {
        val previousTab = MainTab.entries[tabIndex]
        if (target == previousTab) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (target !in visitedTabs) visitedTabs.add(target)
        outgoingTab = previousTab
        tabIndex = target.ordinal
        scope.launch {
            tabFade.snapTo(0f)
            tabFade.animateTo(
                targetValue = 1f,
                animationSpec = tween(TAB_FADE_MS, easing = FastOutSlowInEasing),
            )
            outgoingTab = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { selectTab(item) },
                        icon = {
                            if (item == MainTab.DOWNLOADS) {
                                DownloadTabIcon(item)
                            } else {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .clipToBounds(),
        ) {
            val tabsToCompose = if (currentTab in visitedTabs) {
                visitedTabs.toList()
            } else {
                visitedTabs + currentTab
            }
            tabsToCompose.forEach { item ->
                val isCurrent = item == currentTab
                val isOutgoing = item == outgoingTab
                val isShown = isCurrent || isOutgoing
                key(item) {
                    Box(
                        modifier = if (isShown) {
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (isCurrent) 1f else 0f)
                                .graphicsLayer {
                                    alpha = if (isCurrent) {
                                        tabFade.value
                                    } else if (isOutgoing) {
                                        1f - tabFade.value
                                    } else {
                                        0f
                                    }
                                    translationY = if (isCurrent) {
                                        (1f - tabFade.value) * 18f
                                    } else {
                                        0f
                                    }
                                }
                        } else {
                            Modifier.layout { _, _ -> layout(0, 0) {} }
                        },
                    ) {
                        when (item) {
                            MainTab.HOME -> HomeScreen(
                                initialText = initialText,
                                onParse = onParse,
                                onOpenRecent = { selectedAweme = it },
                                onOpenLibrary = { selectTab(MainTab.LIBRARY) },
                                clipboardLink = clipboardLink,
                                onDismissClipboard = { clipboardLink = null },
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            )

                            MainTab.LIBRARY -> LibraryScreen(
                                onOpen = { selectedAweme = it },
                            )

                            MainTab.DOWNLOADS -> DownloadsScreen()

                            MainTab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = detailVisible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 10 },
        exit = fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { it / 14 },
    ) {
        BackHandler(enabled = detailVisible) { selectedAweme = null }
        val aweme = selectedAweme ?: lastOpenedAweme
        if (aweme != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
            ) {
                ResultScreen(
                    result = ParseBatchResult(results = listOf(aweme), failures = emptyList()),
                    onBack = { selectedAweme = null },
                )
            }
        }
    }
}

@Composable
private fun DownloadTabIcon(item: MainTab) {
    val activeCount by remember {
        DownloadRegistry.tasks
            .map { tasks ->
                tasks.count {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
                }
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0)

    if (activeCount > 0) {
        BadgedBox(badge = { Badge { Text(activeCount.toString()) } }) {
            Icon(item.icon, contentDescription = item.label)
        }
    } else {
        Icon(item.icon, contentDescription = item.label)
    }
}

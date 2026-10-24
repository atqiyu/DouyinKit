package com.atqiyu.douyinkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.download.DownloadRegistry
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.parser.ParseBatchResult
import com.atqiyu.douyinkit.ui.downloads.DownloadsScreen
import com.atqiyu.douyinkit.ui.home.HomeScreen
import com.atqiyu.douyinkit.ui.library.LibraryScreen
import com.atqiyu.douyinkit.ui.result.ResultScreen
import com.atqiyu.douyinkit.ui.settings.SettingsScreen

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Default.Home),
    LIBRARY("资料库", Icons.Default.PhotoLibrary),
    DOWNLOADS("下载", Icons.Default.Download),
    SETTINGS("设置", Icons.Default.Settings),
}

@Composable
fun MainScreen(
    initialText: String = "",
    onParse: (String) -> Unit = {},
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedAweme by remember { mutableStateOf<Aweme?>(null) }
    val tab = MainTab.entries[tabIndex]
    val tasks by DownloadRegistry.tasks.collectAsStateWithLifecycle()
    val activeDownloads = tasks.count {
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
    }

    val openAweme = selectedAweme
    if (openAweme != null) {
        ResultScreen(
            result = ParseBatchResult(results = listOf(openAweme), failures = emptyList()),
            onBack = { selectedAweme = null },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = {
                            if (item == MainTab.DOWNLOADS && activeDownloads > 0) {
                                BadgedBox(
                                    badge = { Badge { Text(activeDownloads.toString()) } },
                                ) {
                                    Icon(item.icon, contentDescription = item.label)
                                }
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
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                },
                label = "tab-transition",
            ) { currentTab ->
                when (currentTab) {
                    MainTab.HOME -> HomeScreen(
                        initialText = initialText,
                        onParse = onParse,
                        onOpenRecent = { selectedAweme = it },
                        onOpenLibrary = { tabIndex = MainTab.LIBRARY.ordinal },
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

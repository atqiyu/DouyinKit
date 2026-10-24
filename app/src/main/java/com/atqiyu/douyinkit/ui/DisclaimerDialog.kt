package com.atqiyu.douyinkit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("使用前须知") },
        text = {
            Column {
                Text(
                    text = "DouyinKit 仅用于个人学习，以及备份你已获得授权的内容。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "解析依赖抖音公开页面，可能随页面改版或风控失效，不提供验证码破解能力。请遵守抖音服务条款和内容版权，不要用于批量抓取、商业分发或传播他人作品。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "解析历史、收藏、Cookie 与下载记录都只保存在本机，App 不上传这些数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("同意并继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("退出")
            }
        },
    )
}

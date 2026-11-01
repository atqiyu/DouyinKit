package com.atqiyu.douyinkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atqiyu.douyinkit.ui.components.AppDialogSystemBars
import com.atqiyu.douyinkit.ui.components.AppMark

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        AppDialogSystemBars()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppMark(size = 54.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "用户协议与使用须知",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "首次使用前请阅读并同意以下说明",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AgreementSection(
                            title = "1. 使用范围",
                            body = "DouyinKit 仅用于个人学习、研究，以及备份你已获得授权的内容。" +
                                "不得用于商业用途、批量抓取、传播他人作品或绕过平台访问限制。",
                        )
                        AgreementSection(
                            title = "2. 数据与隐私",
                            body = "解析记录、下载记录、收藏、标签和 WebView Cookie 只保存在本机，" +
                                "App 不上传这些数据，也不包含广告或第三方分析 SDK。",
                        )
                        AgreementSection(
                            title = "3. 内容版权",
                            body = "作品、音乐、图片和商标的权利归原作者及平台所有。" +
                                "请遵守抖音服务条款和版权规定，不要移除作者权利标识或用于侵权用途。",
                        )
                        AgreementSection(
                            title = "4. 技术限制",
                            body = "解析依赖抖音公开网页和本机 WebView，页面改版、登录要求或安全验证可能导致解析失败。" +
                                "本项目不提供验证码破解；链接可能约 1 小时后过期，不保证所有作品都能解析。",
                        )
                        AgreementSection(
                            title = "5. 免责声明",
                            body = "使用本软件产生的账号、数据、版权或其他风险由使用者自行承担。" +
                                "开发者不对因使用本软件造成的直接或间接损失负责。",
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { agreed = !agreed }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = agreed,
                            onCheckedChange = null,
                        )
                        Text(
                            text = "我已阅读并同意以上用户协议与免责声明",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = onAccept,
                        enabled = agreed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text("同意并继续")
                    }
                    TextButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("不同意，退出")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgreementSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

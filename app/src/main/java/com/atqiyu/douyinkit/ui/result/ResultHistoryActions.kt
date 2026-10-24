package com.atqiyu.douyinkit.ui.result

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.parser.ParseActivity
import com.atqiyu.douyinkit.ui.theme.BrandPink
import kotlinx.coroutines.launch

@Composable
fun ResultHistoryActions(aweme: Aweme) {
    val context = LocalContext.current
    val repository = context.appContainer.historyRepository
    val entity by repository.observe(aweme.awemeId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var showTagsDialog by remember { mutableStateOf(false) }
    val favorite = entity?.isFavorite == true
    val tags = entity?.let(repository::tagsOf).orEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    repository.setFavorite(aweme.awemeId, !favorite)
                }
            },
        ) {
            Icon(
                imageVector = if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (favorite) "取消收藏" else "收藏",
                tint = if (favorite) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (favorite) "已收藏" else "收藏",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.width(8.dp))

        TextButton(onClick = { showTagsDialog = true }) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (tags.isEmpty()) "添加标签" else tags.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = {
                val intent = ParseActivity.intent(context, listOf(aweme.canonicalUrl))
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("重新解析")
        }
    }

    if (showTagsDialog) {
        TagEditDialog(
            initialTags = tags,
            onDismiss = { showTagsDialog = false },
            onSave = { text ->
                showTagsDialog = false
                scope.launch {
                    repository.setTags(aweme.awemeId, text.split(',', '，', '、'))
                }
            },
        )
    }
}

@Composable
fun TagEditDialog(
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember {
        mutableStateOf(initialTags.joinToString("，"))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签") },
                    placeholder = { Text("例如：摄影，旅行，收藏") },
                    supportingText = { Text("用逗号、中文逗号或顿号分隔，最多 12 个") },
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmreader.data.DownloadRepository
import com.jmreader.data.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads = remember { DownloadRepository.of(context) }
    val tasks by downloads.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下載管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("沒有下載任務", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(items = tasks, key = { it.cid }) { task ->
                    DownloadRow(
                        task = task,
                        onPause = { downloads.pause(task.cid) },
                        onResume = { downloads.resume(task.cid) },
                        onDelete = { downloads.delete(task.cid) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = task.title.ifBlank { "章節 ${task.cid}" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${task.completed} / ${task.totalPages}  ·  ${statusLabel(task)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                when {
                    task.isDone -> TextButton(onClick = onDelete) { Text("刪除") }
                    task.isRunning || task.status == DownloadTask.STATUS_PENDING -> {
                        TextButton(onClick = onPause) { Text("暫停") }
                        TextButton(onClick = onDelete) { Text("刪除") }
                    }
                    else -> {
                        TextButton(onClick = onResume) { Text("繼續") }
                        TextButton(onClick = onDelete) { Text("刪除") }
                    }
                }
            }
        }
        if (!task.isDone) {
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

private fun statusLabel(task: DownloadTask): String = when (task.status) {
    DownloadTask.STATUS_DONE -> "已完成"
    DownloadTask.STATUS_RUNNING -> "下載中"
    DownloadTask.STATUS_PAUSED -> "已暫停"
    DownloadTask.STATUS_FAILED -> "失敗"
    else -> "等待中"
}

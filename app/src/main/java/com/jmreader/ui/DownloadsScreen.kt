package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.DownloadRepository
import com.jmreader.data.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads = remember { DownloadRepository.of(context) }
    val tasks by downloads.tasks.collectAsState()

    Scaffold(
        containerColor = IosGroupedBg,
        topBar = {
            TopAppBar(
                title = { Text("下載管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBlue,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosGroupedBg,
                    titleContentColor = IosLabelPrimary,
                ),
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("沒有下載任務", color = IosLabelSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = tasks, key = { it.cid }) { task ->
                    DownloadRow(
                        task = task,
                        onPause = { downloads.pause(task.cid) },
                        onResume = { downloads.resume(task.cid) },
                        onDelete = { downloads.delete(task.cid) },
                    )
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
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = task.title.ifBlank { "章節 ${task.cid}" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${task.completed} / ${task.totalPages}  ·  ${statusLabel(task)}",
                fontSize = 12.sp,
                color = IosLabelSecondary,
            )
            Row {
                when {
                    task.isDone -> TextButton(onClick = onDelete) {
                        Text("刪除", color = MaterialTheme.colorScheme.error)
                    }
                    task.isRunning || task.status == DownloadTask.STATUS_PENDING -> {
                        TextButton(onClick = onPause) { Text("暫停", color = IosBlue) }
                        TextButton(onClick = onDelete) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                    }
                    else -> {
                        TextButton(onClick = onResume) { Text("繼續", color = IosBlue) }
                        TextButton(onClick = onDelete) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (!task.isDone) {
            LinearProgressIndicator(
                progress = { task.progress },
                color = IosBlue,
                trackColor = IosFillGray,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(4.dp)),
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

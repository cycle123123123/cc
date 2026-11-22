package com.jmreader.ui.library

import android.content.Intent

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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmreader.BuildConfig
import com.jmreader.app.CrashLogger
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.components.IosNavigationBar
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosHairline
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * 診斷日誌頁：崩潰堆棧只存在本機，這裡負責看數量、一鍵分享、清除。
 * 沒有後端上報，卸載即清空。
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val toast = rememberToaster()
    var files by remember { mutableStateOf(CrashLogger.files(context)) }
    // 用 count 當 key，清除後強制重算
    var refreshKey by remember { mutableIntStateOf(0) }
    val stamp = remember(refreshKey) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    fun share() {
        val text = runCatching { CrashLogger.reportText(context) }.getOrElse {
            toast("生成報告失敗", true)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "JMReader 診斷報告")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享診斷報告"))
        }.onFailure { toast("沒有可用的分享目標", true) }
    }

    Scaffold(
        containerColor = IosGroupedBg,
        topBar = { IosNavigationBar(title = "診斷日誌", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(IosCardWhite)
                        .border(0.5.dp, IosHairline, shape)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "崩潰日誌",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IosLabelPrimary,
                    )
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} · 記錄 ${files.size} 條",
                        fontSize = 12.sp,
                        color = IosLabelSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "日誌只保存在本機（filesDir/crash），包含版本、機型和堆棧，不含帳號資訊。",
                        fontSize = 11.sp,
                        color = IosLabelSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(onClick = { share() }, shape = RoundedCornerShape(10.dp)) {
                            Text("分享報告", fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = {
                                CrashLogger.clear(context)
                                files = CrashLogger.files(context)
                                refreshKey++
                                toast("已清除")
                            },
                            enabled = files.isNotEmpty(),
                        ) {
                            Text("清除", color = IosPink, fontSize = 13.sp)
                        }
                    }
                }
            }

            items(files, key = { it.absolutePath }) { file ->
                val shape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(IosCardWhite)
                        .border(0.5.dp, IosHairline, shape)
                        .padding(14.dp),
                ) {
                    Text(
                        text = stamp.format(Date(file.lastModified())),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosLabelPrimary,
                    )
                    val preview = remember(file.absolutePath) {
                        runCatching {
                            file.readLines().dropWhile { !it.startsWith("java") && !it.contains("Exception") }
                                .firstOrNull()?.trim()
                        }.getOrNull()
                    }
                    Text(
                        text = preview ?: file.name,
                        fontSize = 11.sp,
                        color = IosLabelSecondary,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 3,
                    )
                }
            }

            if (files.isEmpty()) {
                item {
                    Text(
                        text = "目前沒有崩潰記錄。",
                        fontSize = 13.sp,
                        color = IosLabelSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

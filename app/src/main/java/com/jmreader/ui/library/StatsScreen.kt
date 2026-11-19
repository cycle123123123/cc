package com.jmreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.local.DayStat
import com.jmreader.data.local.ReadingStats
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSeparator

/** 「閱讀統計」頁：全部數字都來自本機 [ReadingStats]，不上傳。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val snapshot by container.readingStats.state.collectAsState()

    val today = ReadingStats.todayKey()
    val activeDays = snapshot.days.filter { it.pages > 0 }.map { it.day }.toSet()
    val streak = ReadingStats.streak(today, activeDays)
    val week = ((today - 6)..today).map { day ->
        snapshot.days.firstOrNull { it.day == day } ?: DayStat(day)
    }
    val maxPages = week.maxOfOrNull { it.pages }?.coerceAtLeast(1) ?: 1
    val weekPages = week.sumOf { it.pages }
    val weekSeconds = week.sumOf { it.seconds }

    Scaffold(
        containerColor = IosGroupedBg,
        topBar = {
            TopAppBar(
                title = { Text("閱讀統計", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        StatCell("累計時長", durationText(snapshot.totalSeconds), Modifier.weight(1f))
                        DividerLine()
                        StatCell("累計頁數", "${snapshot.totalPages}", Modifier.weight(1f))
                        DividerLine()
                        StatCell("累計本數", "${snapshot.albumCount}", Modifier.weight(1f))
                    }
                }
            }

            item {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "連續閱讀",
                                fontSize = 13.sp,
                                color = IosLabelSecondary,
                            )
                            Text(
                                text = if (streak > 0) "$streak 天" else "還沒開始",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = IosLabelPrimary,
                            )
                        }
                        Text(
                            text = if (streak > 0) "保持住" else "打開一本就開始記錄",
                            fontSize = 12.sp,
                            color = IosLabelSecondary,
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "最近 7 天",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosLabelPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$weekPages 頁 · ${durationText(weekSeconds)}",
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                            )
                        }
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            week.forEach { day ->
                                DayBar(
                                    day = day,
                                    isToday = day.day == today,
                                    fraction = if (maxPages == 0) 0f else day.pages.toFloat() / maxPages,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "統計只存在本機，卸載或清除資料後會一併消失；打開不足 5 秒且未翻頁的會話不計入。",
                    fontSize = 11.sp,
                    color = IosLabelSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape),
    ) {
        content()
    }
}

@Composable
private fun StatCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontSize = 12.sp, color = IosLabelSecondary)
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = IosLabelPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .width(0.5.dp)
            .height(28.dp)
            .background(IosSeparator),
    )
}

@Composable
private fun DayBar(day: DayStat, isToday: Boolean, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isToday) "今天" else ReadingStats.weekdayLabel(day.day),
            fontSize = 12.sp,
            color = if (isToday) IosBlue else IosLabelSecondary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(IosSeparator),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isToday) IosBlue else IosBlue.copy(alpha = 0.55f)),
                )
            }
        }
        Text(
            text = if (day.pages > 0) "${day.pages} 頁" else "—",
            fontSize = 11.sp,
            color = IosLabelSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** 把秒數排成「12 小時 5 分」/「8 分鐘」/「不足 1 分鐘」。 */
private fun durationText(seconds: Long): String {
    if (seconds < 60) return if (seconds <= 0) "0 分鐘" else "不足 1 分鐘"
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours <= 0 -> "$minutes 分鐘"
        minutes == 0L -> "$hours 小時"
        else -> "$hours 小時 $minutes 分"
    }
}

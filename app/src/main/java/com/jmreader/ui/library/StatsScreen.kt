package com.jmreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.local.DayStat
import com.jmreader.data.local.ReadingStats
import com.jmreader.ui.common.LocalAppContainer
import com.jmreader.ui.components.IosNavigationBar
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSeparator
import com.jmreader.ui.theme.IosHairline

/** 「閱讀統計」頁：全部數字都來自本機 [ReadingStats]，不上傳。 */
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
    // 只画有记录的那几天：今天没读就不出现今天这根柱子
    val activeWeek = week.filter { it.pages > 0 }
    val maxPages = week.maxOfOrNull { it.pages }?.coerceAtLeast(1) ?: 1
    val weekPages = week.sumOf { it.pages }
    val weekSeconds = week.sumOf { it.seconds }

    Scaffold(
        containerColor = IosGroupedBg,
        topBar = { IosNavigationBar(title = "閱讀統計", onBack = onBack) },
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
                        if (activeWeek.isEmpty()) {
                            Text(
                                text = "最近 7 天還沒有記錄",
                                fontSize = 12.sp,
                                color = IosLabelSecondary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .height(112.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                activeWeek.forEach { day ->
                                    DayColumn(
                                        day = day,
                                        isToday = day.day == today,
                                        fraction = if (maxPages == 0) 0f else day.pages.toFloat() / maxPages,
                                        modifier = Modifier.width(36.dp),
                                    )
                                }
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
            .border(0.5.dp, IosHairline, shape),
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
private fun DayColumn(
    day: DayStat,
    isToday: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${day.pages}",
            fontSize = 10.sp,
            color = if (isToday) IosBlue else IosLabelSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(72.dp * fraction.coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isToday) IosBlue else IosBlue.copy(alpha = 0.45f)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isToday) "今天" else ReadingStats.weekdayLabel(day.day),
            fontSize = 11.sp,
            color = if (isToday) IosBlue else IosLabelSecondary,
            maxLines = 1,
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

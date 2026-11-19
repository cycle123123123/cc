package com.jmreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.WatchUpdate
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosSeparator

/**
 * 「我的」頁頂部的追更提醒：只列韓漫連載的新章節。
 * 點一行直接進詳情；右側「知道了」清掉提醒。
 */
@Composable
internal fun WatchUpdatesCard(
    updates: List<WatchUpdate>,
    onOpen: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "追更提醒",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosLabelPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${updates.size} 本有新話",
                fontSize = 12.sp,
                color = IosBlue,
            )
            TextButton(onClick = onDismiss) {
                Text("知道了", fontSize = 12.sp, color = IosLabelSecondary)
            }
        }
        updates.take(5).forEachIndexed { index, update ->
            if (index > 0) {
                HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(update.albumId) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = update.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosLabelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (update.latestTitle.isNotBlank()) {
                        Text(
                            text = update.latestTitle,
                            fontSize = 11.sp,
                            color = IosLabelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (update.newChapters > 0) "+${update.newChapters} 話" else "新章節",
                    fontSize = 11.sp,
                    color = IosBlue,
                )
            }
        }
        if (updates.size > 5) {
            Text(
                text = "另有 ${updates.size - 5} 本",
                fontSize = 11.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
    }
}

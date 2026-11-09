package com.jmreader.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.model.ChapterEntry
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary

@Composable
internal fun ChapterRow(
    chapter: ChapterEntry,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    isResume: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, if (isResume) IosBlue.copy(alpha = 0.35f) else Color(0x12000000), shape)
            .clickable(onClick = onRead)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 續讀行：左側藍色豎條 + 「續讀」小膠囊，和普通章節一眼區分
        if (isResume) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IosBlue),
            )
        }
        Text(
            text = chapter.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = IosLabelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isResume) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .background(IosBlueSoft)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "續讀",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosBlue,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .background(IosBlueSoft)
                .clickable(onClick = onRead)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = "閱讀",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IosBlue,
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "下載",
                tint = IosBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

package com.jmreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.data.JmRepository
import com.jmreader.data.local.LastRead
import com.jmreader.data.model.AlbumBrief
import com.jmreader.ui.components.AlbumCard
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink

/** 「本地閱讀歷史」小节标题，右侧是清空按钮。 */
@Composable
internal fun LocalHistoryHeader(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "本地閱讀歷史",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelSecondary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("清空", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IosPink)
            }
        }
    }
}

/** 本地历史网格项：3 列，和云端收藏/历史共用 [AlbumCard]。 */
internal fun LazyGridScope.localHistoryEntries(
    repo: JmRepository,
    history: List<LastRead>,
    onOpen: (Long) -> Unit,
) {
    if (history.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("還沒有本地閱讀記錄", fontSize = 14.sp, color = IosLabelSecondary)
            }
        }
    } else {
        items(items = history, key = { "local_${it.albumId}" }) { entry ->
            AlbumCard(
                repo = repo,
                album = entry.asAlbumBrief(),
                onClick = { onOpen(entry.albumId) },
                badge = "第 ${entry.page + 1} 頁",
                compact = true,
            )
        }
    }
}

/**
 * LastRead 只有 id/标题，刚好够 AlbumCard 用：封面由 album.id 拼出，
 * 作者等字段留空。
 */
internal fun LastRead.asAlbumBrief(): AlbumBrief = AlbumBrief(
    id = albumId.toString(),
    name = title.ifBlank { "未命名作品" },
)

@Composable
internal fun LocalHistoryClearDialog(
    visible: Boolean,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空本地閱讀歷史") },
        text = {
            Text(
                if (count > 0) {
                    "將刪除本機保存的 $count 條閱讀記錄，雲端歷史不受影響。"
                } else {
                    "本機目前沒有閱讀記錄。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清空", color = IosPink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

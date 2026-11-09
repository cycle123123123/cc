package com.jmreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.ui.theme.IosSeparator

/**
 * 「工具與設定」分組：未登入 / 已登入共用，四項入口保持一致。
 * [versionName] 只作為「檢查更新」行的右側附註，不影響點擊行為。
 */
@Composable
internal fun SettingsGroup(
    onDownloads: () -> Unit,
    onBlocks: () -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    versionName: String? = null,
) {
    Column {
        Text(
            text = "工具與設定",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(IosCardWhite)
                .border(0.5.dp, Color(0x12000000), shape),
        ) {
            SettingRow(Icons.Filled.Download, "下載管理", IosBlue, onDownloads)
            SettingsDivider()
            SettingRow(Icons.Filled.Block, "屏蔽管理", IosBlue, onBlocks)
            SettingsDivider()
            SettingRow(Icons.Filled.DeleteSweep, "清除快取", IosPink, onClearCache)
            SettingsDivider()
            SettingRow(
                icon = Icons.Filled.Refresh,
                label = "檢查更新",
                tint = IosBlue,
                onClick = onCheckUpdate,
                trailing = versionName?.let { "v$it" },
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = IosLabelPrimary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 13.sp,
                color = IosLabelSecondary,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text("›", fontSize = 18.sp, color = IosLabelSecondary)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = IosSeparator,
        modifier = Modifier.padding(start = 46.dp),
    )
}

package com.jmreader.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.sync.Semaphore
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.components.IosSegmentedControl
import com.jmreader.data.JmRepository
import com.jmreader.data.local.AlbumMeta

@Composable
fun DiscoverScreen(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    val metas = remember { mutableStateMapOf<String, AlbumMeta>() }
    val metaRequests = remember { mutableSetOf<String>() }
    val metaGate = remember { Semaphore(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBg)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBlue,
                        )
                    }
                }
                Text(
                    text = "發現",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosLabelPrimary,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            IosSegmentedControl(
                items = listOf("分類", "標籤", "週榜", "推薦"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }

        when (tab) {
            0 -> CategoryTab(repo, onOpen, metas, metaRequests, metaGate)
            1 -> TagTab(repo, onSearchTag)
            2 -> WeekTab(repo, onOpen, metas, metaRequests, metaGate)
            else -> RecommendTab(repo, onOpen, metas, metaRequests, metaGate)
        }
    }
}

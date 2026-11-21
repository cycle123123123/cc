package com.jmreader.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmreader.data.model.FolderItem

/**
 * 收藏夹的三个弹窗：管理（新建/改名/删除）、改名、把作品移动到收藏夹。
 *
 * 这里只做 UI 与输入校验；网络动作和 Toast 仍由 [LibraryContent] 传入的回调负责，
 * 避免把仓库调用和错误文案散进纯展示组件。
 */
@Composable
internal fun FolderManageDialog(
    visible: Boolean,
    folders: List<FolderItem>,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRenameClick: (FolderItem) -> Unit,
    onDeleteClick: (FolderItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏夾") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = onNewNameChange,
                        label = { Text("新收藏夾") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = { onAdd(newName.trim()) },
                    ) { Text("新增") }
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(folder.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRenameClick(folder) }) { Text("改名") }
                        TextButton(onClick = { onDeleteClick(folder) }) { Text("刪除") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        },
    )
}

@Composable
internal fun FolderRenameDialog(
    folder: FolderItem?,
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (folder == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新命名") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun MoveToFolderDialog(
    folders: List<FolderItem>,
    onPick: (FolderItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移動到收藏夾") },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text("先建立一個收藏夾")
                }
                folders.forEach { folder ->
                    TextButton(onClick = { onPick(folder) }) { Text(folder.name) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

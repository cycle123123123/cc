package com.jmreader.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.CommentItem
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary

/** 评论输入框：回复目标提示 + 输入 + 发送。 */
@Composable
internal fun CommentComposer(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    replyTo: CommentItem?,
    onCancelReply: () -> Unit,
    loggedIn: Boolean,
    posting: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        replyTo?.let { target ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "回覆 @${target.nickname.ifBlank { target.username }}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IosBlue,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelReply) { Text("取消") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentTextChange,
                placeholder = { Text(if (loggedIn) "撰寫評論…" else "登入後可發表評論") },
                enabled = loggedIn && !posting,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSubmit,
                enabled = loggedIn && !posting && commentText.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("發送")
            }
        }
    }
}

@Composable
internal fun CommentRow(
    comment: CommentItem,
    canDelete: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReply = comment.parentCid != "0" && comment.parentCid.isNotBlank()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 20.dp else 0.dp)
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comment.nickname.ifBlank { comment.username },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosBlue,
            )
            Text(
                text = comment.addtime,
                fontSize = 11.sp,
                color = IosLabelSecondary,
            )
        }
        Text(
            text = stripHtml(comment.content),
            fontSize = 14.sp,
            color = IosLabelPrimary,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onLike)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${comment.likes}",
                    fontSize = 12.sp,
                    color = IosLabelSecondary,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onReply)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Reply,
                    contentDescription = null,
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " 回覆",
                    fontSize = 12.sp,
                    color = IosLabelSecondary,
                )
            }
            if (canDelete) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = " 刪除",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 评论内容带 HTML（<div>、<br> 等），展示前转成纯文本。 */
internal fun stripHtml(raw: String): String =
    runCatchingCancellable {
        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }.getOrDefault(raw)

/** 评论列表项；删除权限、点赞/回复/删除回调由屏幕层决定。 */
internal fun LazyListScope.commentItems(
    comments: List<CommentItem>,
    canDelete: (CommentItem) -> Boolean,
    onLike: (CommentItem) -> Unit,
    onReply: (CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit,
) {
    items(items = comments, key = { it.cid }) { comment ->
        CommentRow(
            comment = comment,
            canDelete = canDelete(comment),
            onLike = { onLike(comment) },
            onReply = { onReply(comment) },
            onDelete = { onDelete(comment) },
        )
    }
}

/** 「评论」页的完整内容：输入框 + 空态 / 评论列表。 */
internal fun LazyListScope.commentSection(
    comments: List<CommentItem>,
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    replyTo: CommentItem?,
    onCancelReply: () -> Unit,
    loggedIn: Boolean,
    posting: Boolean,
    onSubmit: () -> Unit,
    canDelete: (CommentItem) -> Boolean,
    onLike: (CommentItem) -> Unit,
    onReply: (CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit,
) {
    item {
        CommentComposer(
            commentText = commentText,
            onCommentTextChange = onCommentTextChange,
            replyTo = replyTo,
            onCancelReply = onCancelReply,
            loggedIn = loggedIn,
            posting = posting,
            onSubmit = onSubmit,
        )
    }
    if (comments.isEmpty()) {
        item {
            Text(
                text = "還沒有評論",
                color = IosLabelSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    } else {
        commentItems(
            comments = comments,
            canDelete = canDelete,
            onLike = onLike,
            onReply = onReply,
            onDelete = onDelete,
        )
    }
}

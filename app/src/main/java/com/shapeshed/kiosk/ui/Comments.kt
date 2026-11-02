package com.shapeshed.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shapeshed.kiosk.R
import com.shapeshed.kiosk.data.Comment
import com.shapeshed.kiosk.data.FlatComment
import com.shapeshed.kiosk.data.relativeTime

/**
 * The comment thread as a modal sheet, summoned from the article's top bar. Opens large (skips the
 * partial state) so tapping "comments" lands you straight in the thread; drag down to dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentsModalSheet(
    commentCount: Int,
    state: UiState<List<FlatComment>>,
    focusCommentId: Long?,
    savedCommentIds: Set<Long>,
    onToggleCommentSaved: (Comment, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val nowSeconds = remember { System.currentTimeMillis() / 1000 }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    // Scroll once to the focused comment when the thread arrives.
    LaunchedEffect(state, focusCommentId) {
        val comments = (state as? UiState.Content)?.data ?: return@LaunchedEffect
        val index = comments.indexOfFirst { it.comment.id == focusCommentId }
        if (index >= 0) listState.scrollToItem(index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { CommentsSheetHandle(commentCount, onDismiss) },
    ) {
        // Bound the height so the LazyColumn has a fixed frame to scroll within.
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                when (state) {
                    is UiState.Loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    is UiState.Error -> item(key = "error") {
                        Text(stringResource(R.string.couldnt_load_comments), Modifier.padding(16.dp))
                    }
                    is UiState.Content -> {
                        if (state.data.isEmpty()) {
                            item(key = "empty") { Text(stringResource(R.string.no_comments), Modifier.padding(16.dp)) }
                        } else {
                            items(state.data, key = { it.comment.id }) { flat ->
                                val saved = flat.comment.id in savedCommentIds
                                CommentRow(
                                    flat = flat,
                                    nowSeconds = nowSeconds,
                                    saved = saved,
                                    focused = flat.comment.id == focusCommentId,
                                    onToggleSaved = { onToggleCommentSaved(flat.comment, !saved) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsSheetHandle(
    commentCount: Int,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 6.dp)) {
            Text(
                text = pluralStringResource(R.plurals.comments_count, commentCount, commentCount),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun CommentRow(
    flat: FlatComment,
    nowSeconds: Long,
    saved: Boolean,
    focused: Boolean,
    onToggleSaved: () -> Unit,
) {
    val indent = (flat.depth.coerceAtMost(6) * 12).dp
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                // A light tint keeps the comment's own text colours readable.
                if (focused) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier,
            )
            .padding(start = 16.dp + indent, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${flat.comment.by}  ·  ${relativeTime(flat.comment.time, nowSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            // Deleted/dead comments have nothing worth keeping.
            if (!flat.comment.deleted && !flat.comment.dead) {
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (saved) R.string.unsave_comment else R.string.save_comment,
                        ),
                        tint = if (saved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = flat.comment.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

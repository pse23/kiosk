package com.shapeshed.kiosk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shapeshed.kiosk.KioskApp
import com.shapeshed.kiosk.R
import com.shapeshed.kiosk.data.LibraryStory
import com.shapeshed.kiosk.data.SavedCommentEntity
import com.shapeshed.kiosk.data.hostOf
import com.shapeshed.kiosk.data.matchesLibraryQuery
import com.shapeshed.kiosk.data.relativeTime
import kotlinx.coroutines.launch

/** What the home screen's list pane shows; picked from the navigation drawer. */
enum class HomeSection { FEEDS, SAVED_STORIES, SAVED_COMMENTS, HISTORY, MUTED, SWIPE }

/**
 * One library list in place of the feed pager. Rows reuse the feed's grouped-card look. Tapping a
 * row opens its story (a saved comment also opens the thread at that comment); [onOpenStory]
 * receives the list's story ids so the article pager can swipe
 * through the library in the same order.
 */
@Composable
internal fun LibraryPane(
    section: HomeSection,
    selectedStoryId: Long?,
    onOpenStory: (ids: List<Long>, id: Long, commentId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as KioskApp
    val scope = rememberCoroutineScope()
    val nowSeconds = remember { System.currentTimeMillis() / 1000 }
    // Each list keeps its own filter text while the drawer switches between them.
    var query by rememberSaveable(section) { mutableStateOf("") }

    when (section) {
        HomeSection.FEEDS, HomeSection.MUTED, HomeSection.SWIPE -> Unit
        HomeSection.SAVED_STORIES -> {
            val stories by app.library.savedStories.collectAsStateWithLifecycle(null)
            StoryLibraryList(
                stories = stories,
                emptyText = stringResource(R.string.no_saved_stories),
                query = query,
                onQueryChange = { query = it },
                searchHint = stringResource(R.string.search_saved_stories),
                selectedStoryId = selectedStoryId,
                nowSeconds = nowSeconds,
                onOpenStory = { ids, id -> onOpenStory(ids, id, null) },
                onUnsave = { id -> scope.launch { app.library.unsaveStory(id) } },
                modifier = modifier,
            )
        }
        HomeSection.HISTORY -> {
            val stories by app.library.history.collectAsStateWithLifecycle(null)
            StoryLibraryList(
                stories = stories,
                emptyText = stringResource(R.string.no_history),
                query = query,
                onQueryChange = { query = it },
                searchHint = stringResource(R.string.search_history),
                selectedStoryId = selectedStoryId,
                nowSeconds = nowSeconds,
                onOpenStory = { ids, id -> onOpenStory(ids, id, null) },
                onUnsave = null,
                header = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scope.launch { app.library.clearHistory() } }) {
                            Text(stringResource(R.string.clear_history))
                        }
                    }
                },
                modifier = modifier,
            )
        }
        HomeSection.SAVED_COMMENTS -> {
            val comments by app.library.savedComments.collectAsStateWithLifecycle(null)
            CommentLibraryList(
                comments = comments,
                query = query,
                onQueryChange = { query = it },
                selectedStoryId = selectedStoryId,
                nowSeconds = nowSeconds,
                onOpenStory = onOpenStory,
                onUnsave = { id -> scope.launch { app.library.unsaveComment(id) } },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun StoryLibraryList(
    stories: List<LibraryStory>?,
    emptyText: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searchHint: String,
    selectedStoryId: Long?,
    nowSeconds: Long,
    onOpenStory: (List<Long>, Long) -> Unit,
    onUnsave: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    if (stories == null) return
    if (stories.isEmpty()) {
        EmptyLibrary(emptyText, modifier)
        return
    }
    val shown = remember(stories, query) {
        stories.filter { matchesLibraryQuery(query, it.title, hostOf(it.url), it.by) }
    }
    val ids = remember(shown) { shown.map(LibraryStory::id) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "search") { LibrarySearchField(query, onQueryChange, searchHint) }
        if (header != null) item(key = "header") { header() }
        if (shown.isEmpty()) item(key = "no-matches") { NoMatches() }
        itemsIndexed(shown, key = { _, story -> story.id }) { index, story ->
            val selected = story.id == selectedStoryId
            LibraryCard(
                selected = selected,
                shape = groupedShape(index, shown.size),
                onClick = { onOpenStory(ids, story.id) },
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { SourceAvatar(url = story.url, title = story.title) },
                    supportingContent = {
                        Text(
                            text = listOfNotNull(
                                hostOf(story.url),
                                relativeTime(story.atMillis / 1000, nowSeconds),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = supportingColor(selected),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = onUnsave?.let { unsave ->
                        { UnsaveButton(onClick = { unsave(story.id) }) }
                    },
                ) {
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentLibraryList(
    comments: List<SavedCommentEntity>?,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedStoryId: Long?,
    nowSeconds: Long,
    onOpenStory: (List<Long>, Long, Long?) -> Unit,
    onUnsave: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (comments == null) return
    if (comments.isEmpty()) {
        EmptyLibrary(stringResource(R.string.no_saved_comments), modifier)
        return
    }
    val shown = remember(comments, query) {
        comments.filter { matchesLibraryQuery(query, it.text, it.by, it.storyTitle) }
    }
    val storyIds = remember(shown) { shown.map(SavedCommentEntity::storyId).distinct() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "search") {
            LibrarySearchField(query, onQueryChange, stringResource(R.string.search_saved_comments))
        }
        if (shown.isEmpty()) item(key = "no-matches") { NoMatches() }
        itemsIndexed(shown, key = { _, c -> c.commentId }) { index, comment ->
            val selected = comment.storyId == selectedStoryId
            LibraryCard(
                selected = selected,
                shape = groupedShape(index, shown.size),
                onClick = { onOpenStory(storyIds, comment.storyId, comment.commentId) },
            ) {
                Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${comment.by}  ·  ${relativeTime(comment.time, nowSeconds)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        UnsaveButton(onClick = { onUnsave(comment.commentId) })
                    }
                    Text(
                        text = comment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                    Text(
                        text = comment.storyTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor(selected),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp, end = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/** Filters the list below it as you type; matching is in [matchesLibraryQuery]. */
@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun NoMatches() {
    Text(
        text = stringResource(R.string.no_library_matches),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UnsaveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = stringResource(R.string.remove_from_saved),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun supportingColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun EmptyLibrary(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun groupedShape(index: Int, size: Int): Shape = when {
    size == 1 -> MaterialTheme.shapes.medium
    index == 0 -> storyFirstShape
    index == size - 1 -> storyLastShape
    else -> storyMiddleShape
}

package com.shapeshed.kiosk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shapeshed.kiosk.KioskApp
import com.shapeshed.kiosk.R
import com.shapeshed.kiosk.data.HnUser
import com.shapeshed.kiosk.data.Story
import com.shapeshed.kiosk.data.hostOf
import com.shapeshed.kiosk.data.relativeTime

/** A commenter's public profile: karma, account age, about text, and their latest stories. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UserProfileSheet(userId: String, onDismiss: () -> Unit) {
    val repository = (LocalContext.current.applicationContext as KioskApp).repository
    val nowSeconds = remember { System.currentTimeMillis() / 1000 }
    val profile by produceState<UiState<Pair<HnUser, List<Story>>>>(UiState.Loading, userId) {
        value = runCatching {
            val user = repository.user(userId) ?: error("no such user $userId")
            user to repository.recentStories(user)
        }.fold({ UiState.Content(it) }, { UiState.Error(it) })
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item(key = "name") {
                Text(
                    text = userId,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            when (val state = profile) {
                is UiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                is UiState.Error -> item(key = "error") {
                    Text(
                        text = stringResource(R.string.couldnt_load_profile),
                        modifier = Modifier.padding(24.dp),
                    )
                }
                is UiState.Content -> {
                    val (user, stories) = state.data
                    item(key = "stats") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.profile_stats,
                                user.karma,
                                user.karma,
                                relativeTime(user.created, nowSeconds),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
                        )
                    }
                    user.about?.let { about ->
                        item(key = "about") {
                            Text(
                                text = about,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                    if (stories.isNotEmpty()) {
                        item(key = "stories-heading") {
                            Text(
                                text = stringResource(R.string.recent_stories),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                            )
                        }
                        items(stories, key = { it.id }) { story ->
                            HorizontalDivider()
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                                Text(
                                    text = story.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(
                                        hostOf(story.url),
                                        pluralStringResource(R.plurals.points, story.score, story.score),
                                        relativeTime(story.time, nowSeconds),
                                    ).joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

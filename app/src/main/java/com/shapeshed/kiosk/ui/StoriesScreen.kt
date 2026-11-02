package com.shapeshed.kiosk.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shapeshed.kiosk.KioskApp
import com.shapeshed.kiosk.R
import com.shapeshed.kiosk.data.Feed
import com.shapeshed.kiosk.data.SearchFilter
import com.shapeshed.kiosk.data.SearchSort
import com.shapeshed.kiosk.data.Story
import com.shapeshed.kiosk.data.hostOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val FEEDS = Feed.entries
private val storyRoundedRadius = 12.dp
private val storySquareRadius = 4.dp
internal val storyFirstShape = RoundedCornerShape(
    topStart = storyRoundedRadius,
    topEnd = storyRoundedRadius,
    bottomStart = storySquareRadius,
    bottomEnd = storySquareRadius,
)
internal val storyMiddleShape = RoundedCornerShape(storySquareRadius)
internal val storyLastShape = RoundedCornerShape(
    topStart = storySquareRadius,
    topEnd = storySquareRadius,
    bottomStart = storyRoundedRadius,
    bottomEnd = storyRoundedRadius,
)

/**
 * Home screen: a left-aligned title bar that hides as you scroll (Google News-style), a scrollable
 * row of pill-shaped feed tabs bound to a [HorizontalPager] (swipe left/right or tap to change
 * feed), and the Gmail-style story list. The last-viewed feed is persisted and restored on launch;
 * each feed keeps its own paging state via a keyed [StoriesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StoriesScreen(
    selectedStoryId: Long?,
    articleOpen: Boolean,
    openingArticleFromSearch: Boolean,
    searchBarState: SearchBarState,
    onFeedStoriesChange: (Feed, List<Long>) -> Unit,
    onOpenStory: (Feed?, Long, Boolean) -> Unit,
    onOpenLibraryStory: (List<Long>, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as KioskApp
    // Read the persisted feed once so the pager can start on the right page without a visible jump.
    val startFeed by produceState<Feed?>(initialValue = null) {
        value = app.settings.selectedFeed.first()
    }

    val feed = startFeed
    if (feed == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = FEEDS.indexOf(feed).coerceAtLeast(0),
    ) { FEEDS.size }
    val scope = rememberCoroutineScope()
    val textFieldState = rememberTextFieldState()
    val isSearchExpanded by remember { derivedStateOf { searchBarState.currentValue == SearchBarValue.Expanded } }
    val searchQueryText by remember { derivedStateOf { textFieldState.text.toString() } }
    var selectedFilter by remember { mutableStateOf(SearchFilter.STORIES) }
    var selectedSort by remember { mutableStateOf(SearchSort.RELEVANCE) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory)
    var wasSearchExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var section by rememberSaveable { mutableStateOf(HomeSection.FEEDS) }

    // Back closes the drawer first, then leaves a library list for the feeds.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && section != HomeSection.FEEDS && !articleOpen && !isSearchExpanded) {
        section = HomeSection.FEEDS
    }

    // Material3's SearchBar installs its own PredictiveBackHandler internally (nested inside the
    // expanded layout), which always wins priority over a BackHandler declared here — so a
    // hand-rolled BackHandler for collapsing search on back never actually fires. Instead, react to
    // the state SearchBarState already exposes: clear the query whenever search collapses for a
    // reason other than opening an article. KioskListDetail collapses search BEFORE navigating to a
    // search-originated article (so it's already closed once the article appears, not collapsing
    // visibly underneath it) — openingArticleFromSearch covers exactly that gap, where the collapse
    // has already happened but articleOpen hasn't gone true yet; without it this effect can't tell
    // that apart from the user genuinely exiting search, and clears the query right as the article
    // opens — which is then still gone once the article closes and search is restored.
    LaunchedEffect(isSearchExpanded, articleOpen, openingArticleFromSearch) {
        if (wasSearchExpanded && !isSearchExpanded && !articleOpen && !openingArticleFromSearch) {
            textFieldState.setTextAndPlaceCursorAtEnd("")
        }
        wasSearchExpanded = isSearchExpanded
    }

    // Remember the feed the user settles on, so it reopens next launch.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            app.settings.setSelectedFeed(FEEDS[page])
        }
    }

    LaunchedEffect(searchQueryText, selectedFilter, selectedSort) {
        searchViewModel.search(searchQueryText, selectedFilter, selectedSort)
    }

    val searchInputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {},
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                if (isSearchExpanded) {
                    IconButton(
                        // The text clears reactively once collapsed (see the isSearchExpanded
                        // LaunchedEffect above) — no need to clear it here too.
                        onClick = { scope.launch { searchBarState.animateToCollapsed() } },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                } else {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.open_menu))
                    }
                }
            },
            trailingIcon = {
                if (isSearchExpanded && searchQueryText.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.setTextAndPlaceCursorAtEnd("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Edge swipes would fight the feed pager; the drawer opens from the menu button and closes
        // by swipe once open.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            KioskDrawer(
                section = section,
                selectedFeed = FEEDS[pagerState.currentPage],
                onSelectFeed = { feed ->
                    section = HomeSection.FEEDS
                    scope.launch {
                        drawerState.close()
                        pagerState.scrollToPage(FEEDS.indexOf(feed))
                    }
                },
                onSelectSection = { picked ->
                    section = picked
                    scope.launch { drawerState.close() }
                },
            )
        },
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        SearchBar(
                            state = searchBarState,
                            inputField = searchInputField,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(top = 8.dp, bottom = 8.dp),
                        )
                        if (!isSearchExpanded && section == HomeSection.FEEDS) {
                            FeedTabs(
                                selectedIndex = pagerState.currentPage,
                                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            )
                        }
                    }
                },
            ) { padding ->
                if (section != HomeSection.FEEDS) {
                    LibraryPane(
                        section = section,
                        selectedStoryId = selectedStoryId,
                        onOpenStory = onOpenLibraryStory,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } else HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    key = { FEEDS[it].name },
                    // Compose the neighbouring feed so it starts loading before the user swipes to it.
                    beyondViewportPageCount = 1,
                ) { page ->
                    FeedPane(
                        feed = FEEDS[page],
                        selectedStoryId = selectedStoryId,
                        onStoryOrderChange = onFeedStoriesChange,
                        onOpenStory = onOpenStory,
                        refreshGeneration = refreshGeneration,
                        onRefreshAll = { refreshGeneration++ },
                    )
                }
            }

            ExpandedFullScreenContainedSearchBar(
                state = searchBarState,
                inputField = searchInputField,
            ) {
                SearchResultsPane(
                    query = searchQueryText,
                    selectedStoryId = selectedStoryId,
                    selectedFilter = selectedFilter,
                    selectedSort = selectedSort,
                    onFilterChange = { selectedFilter = it },
                    onSortChange = { selectedSort = it },
                    onOpenStory = { id ->
                        searchViewModel.markViewed(id)
                        onOpenStory(null, id, false)
                    },
                    viewModel = searchViewModel,
                )
            }
        }
    }
}

/** The left navigation drawer: feeds, then the user's library. */
@Composable
private fun KioskDrawer(
    section: HomeSection,
    selectedFeed: Feed,
    onSelectFeed: (Feed) -> Unit,
    onSelectSection: (HomeSection) -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp),
            )
            DrawerHeading(R.string.drawer_feeds)
            FEEDS.forEach { feed ->
                NavigationDrawerItem(
                    label = { Text(stringResource(feed.labelRes())) },
                    selected = section == HomeSection.FEEDS && feed == selectedFeed,
                    onClick = { onSelectFeed(feed) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DrawerHeading(R.string.drawer_library)
            LibraryDrawerItem(HomeSection.SAVED_STORIES, R.string.saved_stories, Icons.Rounded.Bookmarks, section, onSelectSection)
            LibraryDrawerItem(HomeSection.SAVED_COMMENTS, R.string.saved_comments, Icons.Rounded.Forum, section, onSelectSection)
            LibraryDrawerItem(HomeSection.HISTORY, R.string.recently_viewed, Icons.Rounded.History, section, onSelectSection)
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerHeading(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun LibraryDrawerItem(
    target: HomeSection,
    @StringRes label: Int,
    icon: ImageVector,
    section: HomeSection,
    onSelectSection: (HomeSection) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        icon = { Icon(icon, contentDescription = null) },
        selected = section == target,
        onClick = { onSelectSection(target) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/**
 * M3 Primary tabs bound to the pager: Title Small labels with the primary pill bottom-indicator.
 * Scrollable so the active feed stays in view when the labels don't all fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        // Zero edge padding + the Tab's built-in 16dp text padding lands the first label at the
        // same 16dp start inset as the app-bar title, so "Top" aligns under "Kiosk".
        edgePadding = 0.dp,
    ) {
        FEEDS.forEachIndexed { index, feed ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = stringResource(feed.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultsPane(
    query: String,
    selectedStoryId: Long?,
    selectedFilter: SearchFilter,
    selectedSort: SearchSort,
    onFilterChange: (SearchFilter) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    onOpenStory: (Long) -> Unit,
    viewModel: SearchViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val viewedIds by viewModel.viewedIds.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SearchFilterRow(
            selectedFilter = selectedFilter,
            selectedSort = selectedSort,
            onFilterChange = onFilterChange,
            onSortChange = onSortChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (val current = state) {
                is UiState.Loading -> LoadingIndicator()
                is UiState.Error -> ErrorState(onRetry = viewModel::retry)
                is UiState.Content -> when {
                    query.isBlank() -> Text(
                        text = stringResource(R.string.search_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    current.data.isEmpty() -> Text(
                        text = stringResource(R.string.no_search_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> StoryList(
                        stories = current.data,
                        selectedStoryId = selectedStoryId,
                        viewedIds = viewedIds,
                        loadingMore = loadingMore,
                        onLoadMore = viewModel::loadMore,
                        onOpenStory = onOpenStory,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterRow(
    selectedFilter: SearchFilter,
    selectedSort: SearchSort,
    onFilterChange: (SearchFilter) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        SearchSort.entries.forEach { sort ->
            FilterChip(
                selected = selectedSort == sort,
                onClick = { onSortChange(sort) },
                label = { Text(stringResource(sort.labelRes())) },
                leadingIcon = selectedIcon(selectedSort == sort),
            )
        }
        SearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(stringResource(filter.labelRes())) },
                leadingIcon = selectedIcon(selectedFilter == filter),
            )
        }
    }
}

@Composable
private fun selectedIcon(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        }
    } else {
        null
    }

/** One feed's story list, with its own [StoriesViewModel] keyed by feed so state survives swipes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedPane(
    feed: Feed,
    selectedStoryId: Long?,
    onStoryOrderChange: (Feed, List<Long>) -> Unit,
    onOpenStory: (Feed?, Long, Boolean) -> Unit,
    refreshGeneration: Int,
    onRefreshAll: () -> Unit,
    viewModel: StoriesViewModel = viewModel(
        key = "feed-${feed.name}",
        factory = StoriesViewModel.factory(feed),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val viewedIds by viewModel.viewedIds.collectAsStateWithLifecycle()

    LaunchedEffect(feed, refreshGeneration) {
        if (refreshGeneration > 0) viewModel.refresh()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorState(onRetry = viewModel::load)
            is UiState.Content -> {
                LaunchedEffect(feed, current.data) {
                    onStoryOrderChange(feed, current.data.map(Story::id))
                }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefreshAll,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    StoryList(
                        stories = current.data,
                        selectedStoryId = selectedStoryId,
                        viewedIds = viewedIds,
                        loadingMore = loadingMore,
                        refreshing = refreshing,
                        refreshGeneration = refreshGeneration,
                        onLoadMore = viewModel::loadMore,
                        onOpenStory = { id ->
                            viewModel.markViewed(id)
                            onOpenStory(feed, id, false)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoryList(
    stories: List<Story>,
    selectedStoryId: Long?,
    viewedIds: Set<Long>,
    loadingMore: Boolean,
    refreshing: Boolean = false,
    refreshGeneration: Int = 0,
    onLoadMore: () -> Unit,
    onOpenStory: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(refreshGeneration, refreshing) {
        if (refreshGeneration > 0 && !refreshing) {
            withFrameNanos { }
            listState.scrollToItem(0)
        }
    }
    // Load the next page once the last few rows come into view.
    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onLoadMore() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(stories, key = { _, story -> story.id }) { index, story ->
            StoryCard(
                story = story,
                selected = story.id == selectedStoryId,
                viewed = story.id in viewedIds,
                shape = when {
                    stories.size == 1 -> MaterialTheme.shapes.medium
                    index == 0 -> storyFirstShape
                    index == stories.lastIndex -> storyLastShape
                    else -> storyMiddleShape
                },
                onClick = { onOpenStory(story.id) },
            )
        }
        if (loadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    story: Story,
    selected: Boolean,
    viewed: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val titleColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        viewed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { SourceAvatar(url = story.url, title = story.title) },
            supportingContent = hostOf(story.url)?.let { host ->
                {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                // Unread stories are bold, like Gmail; opened ones render normal weight.
                fontWeight = if (viewed) FontWeight.Normal else FontWeight.Bold,
                color = titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.couldnt_load))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@StringRes
private fun Feed.labelRes(): Int = when (this) {
    Feed.TOP -> R.string.feed_top
    Feed.NEW -> R.string.feed_new
    Feed.BEST -> R.string.feed_best
    Feed.ASK -> R.string.feed_ask
    Feed.SHOW -> R.string.feed_show
    Feed.JOBS -> R.string.feed_jobs
}

@StringRes
private fun SearchSort.labelRes(): Int = when (this) {
    SearchSort.RELEVANCE -> R.string.search_sort_relevance
    SearchSort.DATE -> R.string.search_sort_date
}

@StringRes
private fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.ALL -> R.string.search_filter_all
    SearchFilter.STORIES -> R.string.search_filter_stories
    SearchFilter.ASK -> R.string.search_filter_ask
    SearchFilter.SHOW -> R.string.search_filter_show
    SearchFilter.JOBS -> R.string.search_filter_jobs
}

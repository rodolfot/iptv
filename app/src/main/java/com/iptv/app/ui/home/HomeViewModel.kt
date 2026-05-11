package com.iptv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.data.cache.CatalogCacheRepository
import com.iptv.app.data.cache.toDomain
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.data.epg.EpgRepository
import com.iptv.app.data.db.CategoryCacheDao
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.db.LiveCacheDao
import com.iptv.app.data.db.MovieCacheDao
import com.iptv.app.data.db.SeriesCacheDao
import com.iptv.app.data.db.WatchlistDao
import com.iptv.app.data.db.WatchlistEntity
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.domain.sort.sorted
import com.iptv.app.domain.sort.sortedMovies
import com.iptv.app.domain.sort.sortedSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryListState(
    val loading: Boolean = false,
    val items: List<Category> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val error: String? = null
)

data class ChannelsState(
    val loading: Boolean = false,
    val items: List<LiveChannel> = emptyList(),
    val error: String? = null
)

data class MoviesState(
    val loading: Boolean = false,
    val items: List<Movie> = emptyList(),
    val error: String? = null
)

data class SeriesState(
    val loading: Boolean = false,
    val items: List<Series> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cache: CatalogCacheRepository,
    private val epg: EpgRepository,
    private val categoryDao: CategoryCacheDao,
    private val liveDao: LiveCacheDao,
    private val movieDao: MovieCacheDao,
    private val seriesDao: SeriesCacheDao,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val settings: SettingsStore,
    private val currentProfile: CurrentProfile
) : ViewModel() {

    private val _epgNow = MutableStateFlow<Map<String, EpgProgrammeEntity>>(emptyMap())
    val epgNow = _epgNow.asStateFlow()

    val settingsFlow: StateFlow<com.iptv.app.data.prefs.AppSettings> =
        settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, com.iptv.app.data.prefs.AppSettings())

    private val _liveCategories = MutableStateFlow(CategoryListState())
    val liveCategories = _liveCategories.asStateFlow()

    private val _movieCategories = MutableStateFlow(CategoryListState())
    val movieCategories = _movieCategories.asStateFlow()

    private val _seriesCategories = MutableStateFlow(CategoryListState())
    val seriesCategories = _seriesCategories.asStateFlow()

    private val _channels = MutableStateFlow(ChannelsState())
    val channels = _channels.asStateFlow()

    private val _movies = MutableStateFlow(MoviesState())
    val movies = _movies.asStateFlow()

    private val _series = MutableStateFlow(SeriesState())
    val series = _series.asStateFlow()

    // Re-subscribe whenever the active profile changes so the user immediately
    // sees the rows scoped to that profile.
    val favorites = settings.flow
        .map { currentProfile.id() }
        .distinctUntilChanged()
        .flatMapLatest { favoriteDao.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watchlist = settings.flow
        .map { currentProfile.id() }
        .distinctUntilChanged()
        .flatMapLatest { watchlistDao.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Reactive flag for Kids mode. Re-derived whenever the active profile changes. */
    val kidsMode: StateFlow<Boolean> = settings.flow
        .map { it.profiles.firstOrNull { p -> p.id == it.activeProfileId }?.kids == true }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Allow-listed category tokens ("live:42") for Kids mode; empty when not Kids. */
    val kidsAllowedCategories: StateFlow<Set<String>> = settings.flow
        .map { s ->
            val active = s.profiles.firstOrNull { p -> p.id == s.activeProfileId }
            if (active?.kids == true) active.allowedCategories else emptySet()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _initialLoading = MutableStateFlow(false)
    val initialLoading = _initialLoading.asStateFlow()

    /**
     * Run on Home entry. If the cache is completely empty (first run, post-clear, or
     * forced manual refresh), block the UI with the loading screen until refresh finishes.
     * Otherwise, return immediately and let the per-section loaders handle stale-while-revalidate.
     */
    fun bootstrapCatalog(force: Boolean = false) {
        viewModelScope.launch {
            val movieCount = movieDao.observeAll().firstOrEmpty().size
            val seriesCount = seriesDao.observeAll().firstOrEmpty().size
            val liveCount = liveDao.observeAll().firstOrEmpty().size
            val empty = movieCount == 0 && seriesCount == 0 && liveCount == 0
            if (!empty && !force) return@launch
            _initialLoading.value = true
            cache.refreshAll()
            _initialLoading.value = false
            _lastUpdatedAt.value = cache.lastUpdatedAt()
        }
    }

    fun loadLiveCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _liveCategories.value = _liveCategories.value.copy(loading = true, error = null)
            val cached = categoryDao.get(ContentType.LIVE).map { it.toDomain() }
            if (cached.isNotEmpty()) {
                _liveCategories.value = CategoryListState(items = cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.LIVE_CATEGORIES)
            if (needsFetch) {
                cache.refreshLiveCategories()
                    .onSuccess {
                        _liveCategories.value = CategoryListState(
                            items = categoryDao.get(ContentType.LIVE).map { it.toDomain() }
                        )
                    }
                    .onFailure { e ->
                        _liveCategories.value = _liveCategories.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _liveCategories.value = _liveCategories.value.copy(loading = false)
            }
        }
    }

    fun loadMovieCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _movieCategories.value = _movieCategories.value.copy(loading = true, error = null)
            val cached = categoryDao.get(ContentType.MOVIE).map { it.toDomain() }
            if (cached.isNotEmpty()) {
                _movieCategories.value = CategoryListState(items = cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.MOVIE_CATEGORIES)
            if (needsFetch) {
                cache.refreshMovieCategories()
                    .onSuccess {
                        _movieCategories.value = CategoryListState(
                            items = categoryDao.get(ContentType.MOVIE).map { it.toDomain() }
                        )
                    }
                    .onFailure { e ->
                        _movieCategories.value = _movieCategories.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _movieCategories.value = _movieCategories.value.copy(loading = false)
            }
        }
    }

    fun loadSeriesCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _seriesCategories.value = _seriesCategories.value.copy(loading = true, error = null)
            val cached = categoryDao.get(ContentType.SERIES).map { it.toDomain() }
            if (cached.isNotEmpty()) {
                _seriesCategories.value = CategoryListState(items = cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.SERIES_CATEGORIES)
            if (needsFetch) {
                cache.refreshSeriesCategories()
                    .onSuccess {
                        _seriesCategories.value = CategoryListState(
                            items = categoryDao.get(ContentType.SERIES).map { it.toDomain() }
                        )
                    }
                    .onFailure { e ->
                        _seriesCategories.value = _seriesCategories.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _seriesCategories.value = _seriesCategories.value.copy(loading = false)
            }
        }
    }

    fun loadChannels(categoryId: String?, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _channels.value = ChannelsState(loading = true)
            val sort = settingsFlow.value.liveSort
            val applyFilter: (List<com.iptv.app.data.db.LiveChannelCacheEntity>) -> List<LiveChannel> = { all ->
                all.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .map { it.toDomain() }
                    .toList()
                    .sorted(sort)
            }
            val cached = applyFilter(liveDao.observeAll().firstOrEmpty())
            if (cached.isNotEmpty()) {
                _channels.value = ChannelsState(items = cached)
                refreshEpgNowFor(cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.LIVE_STREAMS)
            if (needsFetch) {
                cache.refreshLiveStreams()
                    .onSuccess {
                        val items = applyFilter(liveDao.observeAll().firstOrEmpty())
                        _channels.value = ChannelsState(items = items)
                        refreshEpgNowFor(items)
                    }
                    .onFailure { e ->
                        _channels.value = _channels.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _channels.value = _channels.value.copy(loading = false)
            }
        }
    }

    fun loadMovies(categoryId: String?, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _movies.value = MoviesState(loading = true)
            val sort = settingsFlow.value.moviesSort
            val applyFilter: (List<com.iptv.app.data.db.MovieCacheEntity>) -> List<Movie> = { all ->
                all.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .map { it.toDomain() }
                    .toList()
                    .sortedMovies(sort)
            }
            val cached = applyFilter(movieDao.observeAll().firstOrEmpty())
            if (cached.isNotEmpty()) {
                _movies.value = MoviesState(items = cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.MOVIE_STREAMS)
            if (needsFetch) {
                cache.refreshMovieStreams()
                    .onSuccess {
                        _movies.value = MoviesState(items = applyFilter(movieDao.observeAll().firstOrEmpty()))
                    }
                    .onFailure { e ->
                        _movies.value = _movies.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _movies.value = _movies.value.copy(loading = false)
            }
        }
    }

    fun loadSeries(categoryId: String?, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _series.value = SeriesState(loading = true)
            val sort = settingsFlow.value.seriesSort
            val applyFilter: (List<com.iptv.app.data.db.SeriesCacheEntity>) -> List<Series> = { all ->
                all.asSequence()
                    .filter { categoryId == null || it.categoryId == categoryId }
                    .map { it.toDomain() }
                    .toList()
                    .sortedSeries(sort)
            }
            val cached = applyFilter(seriesDao.observeAll().firstOrEmpty())
            if (cached.isNotEmpty()) {
                _series.value = SeriesState(items = cached)
            }
            val needsFetch = cached.isEmpty() || forceRefresh || cache.isStale(CatalogCacheRepository.Scope.SERIES_LIST)
            if (needsFetch) {
                cache.refreshSeriesList()
                    .onSuccess {
                        _series.value = SeriesState(items = applyFilter(seriesDao.observeAll().firstOrEmpty()))
                    }
                    .onFailure { e ->
                        _series.value = _series.value.copy(
                            loading = false,
                            error = if (cached.isEmpty()) e.message else null
                        )
                    }
            } else {
                _series.value = _series.value.copy(loading = false)
            }
        }
    }

    fun setSort(scope: SortScope, option: SortOption) {
        viewModelScope.launch {
            settings.setSort(scope, option)
            when (scope) {
                SortScope.LIVE -> _channels.value = _channels.value.copy(items = _channels.value.items.sorted(option))
                SortScope.MOVIES -> _movies.value = _movies.value.copy(items = _movies.value.items.sortedMovies(option))
                SortScope.SERIES -> _series.value = _series.value.copy(items = _series.value.items.sortedSeries(option))
                SortScope.FAVORITES -> { /* favorites screen handles its own sort */ }
            }
        }
    }

    fun toggleFavorite(entity: FavoriteEntity) {
        viewModelScope.launch {
            val pid = currentProfile.id()
            val scoped = entity.copy(profileId = pid)
            val existing = favorites.value.firstOrNull { it.type == scoped.type && it.itemId == scoped.itemId }
            if (existing == null) favoriteDao.insert(scoped) else favoriteDao.delete(pid, scoped.type, scoped.itemId)
        }
    }

    fun toggleWatchlist(entity: WatchlistEntity) {
        viewModelScope.launch {
            val pid = currentProfile.id()
            val scoped = entity.copy(profileId = pid)
            val existing = watchlist.value.firstOrNull { it.type == scoped.type && it.itemId == scoped.itemId }
            if (existing == null) watchlistDao.insert(scoped) else watchlistDao.delete(pid, scoped.type, scoped.itemId)
        }
    }

    fun logout() {
        viewModelScope.launch { settings.setLoggedOut() }
    }

    fun setParentalPin(pin: String) {
        viewModelScope.launch { settings.setPin(pin) }
    }

    /**
     * Cross-category typed search used by the section-level local filter:
     * when the user is on the category grid and types a query, in addition to
     * filtering category names we also surface matching items so they can jump
     * straight from e.g. "Pokemon" → the series, without picking a category.
     */
    suspend fun searchMoviesByName(query: String, limit: Int = 60): List<Movie> =
        if (query.trim().length < 2) emptyList()
        else cache.searchMovies(query, limit).map { it.toDomain() }

    suspend fun searchSeriesByName(query: String, limit: Int = 60): List<Series> =
        if (query.trim().length < 2) emptyList()
        else cache.searchSeries(query, limit).map { it.toDomain() }

    suspend fun searchLiveByName(query: String, limit: Int = 60): List<LiveChannel> =
        if (query.trim().length < 2) emptyList()
        else cache.searchLive(query, limit).map { it.toDomain() }

    fun refreshAll() {
        loadLiveCategories(forceRefresh = true)
        loadMovieCategories(forceRefresh = true)
        loadSeriesCategories(forceRefresh = true)
    }

    private val _lastUpdatedAt = MutableStateFlow<Long?>(null)
    val lastUpdatedAt = _lastUpdatedAt.asStateFlow()

    init {
        viewModelScope.launch { _lastUpdatedAt.value = cache.lastUpdatedAt() }
    }

    fun refreshLastUpdatedAt() {
        viewModelScope.launch { _lastUpdatedAt.value = cache.lastUpdatedAt() }
    }

    private fun refreshEpgNowFor(items: List<LiveChannel>) {
        val ids = items.mapNotNull { it.epgChannelId }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (epg.isStale()) {
                runCatching { epg.refresh() }
            }
            val now = epg.currentForChannels(ids)
            if (now.isNotEmpty()) _epgNow.value = _epgNow.value + now
        }
    }
}

// Helper used above to grab the current snapshot of a Flow without subscribing.
private suspend fun <T> Flow<List<T>>.firstOrEmpty(): List<T> =
    try {
        this.first()
    } catch (_: Throwable) {
        emptyList()
    }

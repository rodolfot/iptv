package com.iptv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series
import com.iptv.app.domain.model.toModel
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.domain.sort.sorted
import com.iptv.app.domain.sort.sortedMovies
import com.iptv.app.domain.sort.sortedSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val favoriteDao: FavoriteDao,
    private val settings: SettingsStore
) : ViewModel() {

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

    val favorites = favoriteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadLiveCategories() {
        viewModelScope.launch {
            _liveCategories.value = _liveCategories.value.copy(loading = true, error = null)
            runCatching { repo.liveCategories() }
                .onSuccess { dto ->
                    val extra = settingsFlow.value.extraAdultCategoryIds
                    val cats = dto.map { it.toModel(ContentType.LIVE, extra) }
                        .sortedBy { it.name.lowercase() }
                    _liveCategories.value = CategoryListState(items = cats)
                }
                .onFailure { _liveCategories.value = CategoryListState(error = it.message) }
        }
    }

    fun loadMovieCategories() {
        viewModelScope.launch {
            _movieCategories.value = _movieCategories.value.copy(loading = true, error = null)
            runCatching { repo.vodCategories() }
                .onSuccess { dto ->
                    val extra = settingsFlow.value.extraAdultCategoryIds
                    val cats = dto.map { it.toModel(ContentType.MOVIE, extra) }
                        .sortedBy { it.name.lowercase() }
                    _movieCategories.value = CategoryListState(items = cats)
                }
                .onFailure { _movieCategories.value = CategoryListState(error = it.message) }
        }
    }

    fun loadSeriesCategories() {
        viewModelScope.launch {
            _seriesCategories.value = _seriesCategories.value.copy(loading = true, error = null)
            runCatching { repo.seriesCategories() }
                .onSuccess { dto ->
                    val extra = settingsFlow.value.extraAdultCategoryIds
                    val cats = dto.map { it.toModel(ContentType.SERIES, extra) }
                        .sortedBy { it.name.lowercase() }
                    _seriesCategories.value = CategoryListState(items = cats)
                }
                .onFailure { _seriesCategories.value = CategoryListState(error = it.message) }
        }
    }

    fun loadChannels(categoryId: String?) {
        viewModelScope.launch {
            _channels.value = ChannelsState(loading = true)
            runCatching { repo.liveStreams(categoryId) }
                .onSuccess { dto ->
                    val sort = settingsFlow.value.liveSort
                    val items = dto.map { it.toModel() }.sorted(sort)
                    _channels.value = ChannelsState(items = items)
                }
                .onFailure { _channels.value = ChannelsState(error = it.message) }
        }
    }

    fun loadMovies(categoryId: String?) {
        viewModelScope.launch {
            _movies.value = MoviesState(loading = true)
            runCatching { repo.vodStreams(categoryId) }
                .onSuccess { dto ->
                    val sort = settingsFlow.value.moviesSort
                    val items = dto.map { it.toModel() }.sortedMovies(sort)
                    _movies.value = MoviesState(items = items)
                }
                .onFailure { _movies.value = MoviesState(error = it.message) }
        }
    }

    fun loadSeries(categoryId: String?) {
        viewModelScope.launch {
            _series.value = SeriesState(loading = true)
            runCatching { repo.series(categoryId) }
                .onSuccess { dto ->
                    val sort = settingsFlow.value.seriesSort
                    val items = dto.map { it.toModel() }.sortedSeries(sort)
                    _series.value = SeriesState(items = items)
                }
                .onFailure { _series.value = SeriesState(error = it.message) }
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
            val existing = favorites.value.firstOrNull { it.type == entity.type && it.itemId == entity.itemId }
            if (existing == null) favoriteDao.insert(entity) else favoriteDao.delete(entity.type, entity.itemId)
        }
    }

    fun logout() {
        viewModelScope.launch { settings.setLoggedOut() }
    }

    fun refreshAll() {
        loadLiveCategories(); loadMovieCategories(); loadSeriesCategories()
    }
}

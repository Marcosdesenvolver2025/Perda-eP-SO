package com.musibox.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musibox.app.AppContainer
import com.musibox.app.data.db.Song
import com.musibox.app.data.prefs.LibrarySort
import com.musibox.app.data.repo.MostPlayed
import com.musibox.app.data.repo.PlaylistSummary
import com.musibox.app.data.repo.ScanState
import com.musibox.app.data.repo.TrackRef
import com.musibox.app.data.repo.displayArtist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val sortFlow = c.settings.settings.map { it.librarySort }.distinctUntilChanged()

    val sort: StateFlow<LibrarySort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.TITLE)

    val songs: StateFlow<List<Song>?> = combine(c.music.songs, sortFlow) { list, sort -> sortSongs(list, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val scanState: StateFlow<ScanState> = c.music.scanState

    val playlists: StateFlow<List<PlaylistSummary>?> =
        c.library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favorites: StateFlow<List<TrackRef>?> =
        c.library.favoriteTracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteKeys: StateFlow<Set<String>> =
        c.library.favoriteKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val mostPlayed: StateFlow<List<MostPlayed>?> =
        c.library.mostPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rescan() {
        viewModelScope.launch { c.music.rescan() }
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { c.settings.setLibrarySort(sort) }
    }

    fun createPlaylist(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.library.createPlaylist(name)) }
    }

    companion object {
        fun sortSongs(list: List<Song>, sort: LibrarySort): List<Song> = when (sort) {
            LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> list.sortedWith(compareBy({ it.displayArtist().lowercase() }, { it.title.lowercase() }))
            LibrarySort.RECENT -> list.sortedByDescending { it.dateAddedMs }
        }
    }
}

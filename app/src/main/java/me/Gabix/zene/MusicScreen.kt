package me.Gabix.zene

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun MusicScreen(
    songs: List<AudioItem>,
    repeatMode: Int,
    isShuffleMode: Boolean,
    isPlaying: Boolean,
    currentMediaId: String?,
    playbackPosition: Long,
    playbackDuration: Long,
    appVolume: Float,
    onSongSelected: (AudioItem, List<AudioItem>) -> Unit,
    onCycleMode: () -> Unit,
    onDeleteSong: (AudioItem) -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Összes dal, 1 = Előadók
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Előadók kinyerése és szétbontása feat-ek esetén
    fun splitArtists(artistString: String): List<String> {
        val delimiters = Regex("[,&/]|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+", RegexOption.IGNORE_CASE)
        return artistString.split(delimiters)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) && !it.equals("ismeretlen előadó", ignoreCase = true) }
            .ifEmpty { listOf("Ismeretlen előadó") }
    }

    val artistsGrouped = remember(songs) {
        val map = mutableMapOf<String, MutableList<AudioItem>>()
        songs.forEach { song ->
            val artists = splitArtists(song.artist)
            artists.forEach { artist ->
                map.getOrPut(artist) { mutableListOf() }.add(song)
            }
        }
        map.toSortedMap()
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                        song.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val isModeActive = isShuffleMode || repeatMode != Player.REPEAT_MODE_OFF

    val modeLabel = when {
        isShuffleMode -> "Keverés"
        repeatMode == Player.REPEAT_MODE_ONE -> "Egy szám ismétlése"
        repeatMode == Player.REPEAT_MODE_ALL -> "Összes ismétlése"
        else -> "Nincs ismétlés"
    }

    val modeIcon: ImageVector = when {
        isShuffleMode -> ShuffleIcon
        repeatMode == Player.REPEAT_MODE_ONE -> RepeatOneIcon
        repeatMode == Player.REPEAT_MODE_ALL -> RepeatIcon
        else -> RepeatIcon
    }

    val currentPlayingSong = remember(currentMediaId, songs) {
        songs.find { it.id.toString() == currentMediaId }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsView(
                appVolume = appVolume,
                onVolumeChanged = onVolumeChanged,
                onBack = { showSettings = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentPlayingSong != null) 120.dp else 0.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentPlayingSong != null) 120.dp else 0.dp)
            ) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selectedArtist,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "HeaderAnimation"
                    ) { artist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            if (artist != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { selectedArtist = null }) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                                    }
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Lejátszás beállításai",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledIconButton(
                                            onClick = onCycleMode,
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = if (isModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = if (isModeActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = modeIcon,
                                                contentDescription = "Lejátszási mód"
                                            )
                                        }

                                        IconButton(
                                            onClick = { showSettings = true },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Beállítások"
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TabRow(selectedTabIndex = selectedTab) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0; selectedArtist = null },
                                        text = { Text("Összes dal") }
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1; selectedArtist = null },
                                        text = { Text("Előadók") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (artist == null) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    placeholder = {
                                        Text(
                                            "Keresés...",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Keresés",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Keresés törlése",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    AnimatedContent(
                        targetState = Pair(selectedTab, selectedArtist),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
                        },
                        label = "ContentAnimation"
                    ) { (tab, artist) ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (artist != null) {
                                val artistSongs = artistsGrouped[artist] ?: emptyList()
                                SongsListView(
                                    songs = artistSongs,
                                    currentMediaId = currentMediaId,
                                    isPlaying = isPlaying,
                                    onSongSelected = { song -> onSongSelected(song, artistSongs) },
                                    onDeleteSong = onDeleteSong
                                )
                            } else if (tab == 0) {
                                if (filteredSongs.isEmpty()) {
                                    EmptyStateView(text = if (searchQuery.isEmpty()) "Nem találhatók zenefájlok." else "Nincs találat a keresésre.")
                                } else {
                                    SongsListView(
                                        songs = filteredSongs,
                                        currentMediaId = currentMediaId,
                                        isPlaying = isPlaying,
                                        onSongSelected = { song -> onSongSelected(song, filteredSongs) },
                                        onDeleteSong = onDeleteSong
                                    )
                                }
                            } else {
                                val filteredArtists = if (searchQuery.isBlank()) {
                                    artistsGrouped.keys.toList()
                                } else {
                                    artistsGrouped.keys.filter { it.contains(searchQuery, ignoreCase = true) }
                                }

                                if (filteredArtists.isEmpty()) {
                                    EmptyStateView(text = "Nem találhatók előadók.")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(items = filteredArtists) { currentArtist ->
                                            val count = artistsGrouped[currentArtist]?.size ?: 0
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .clickable { selectedArtist = currentArtist }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp)
                                                ) {
                                                    Text(
                                                        text = currentArtist,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "$count dal",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentPlayingSong != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            currentPlayingSong?.let { playingSong ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    IconButton(onClick = { onSongSelected(playingSong, songs) }) {
                                        Icon(
                                            imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Szünet" else "Lejátszás",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = playingSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playingSong.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledIconButton(
                                onClick = onCycleMode,
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (isModeActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = "Lejátszási mód",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        var localSliderValue by remember { mutableStateOf<Float?>(null) }
                        val currentPos = localSliderValue?.toLong() ?: playbackPosition
                        val remainingTime = maxOf(0L, playbackDuration - currentPos)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "-${formatTime(remainingTime)} / ${formatTime(playbackDuration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Slider(
                            value = currentPos.toFloat(),
                            onValueChange = { localSliderValue = it },
                            onValueChangeFinished = {
                                localSliderValue?.let { onSeekTo(it.toLong()) }
                                localSliderValue = null
                            },
                            valueRange = 0f..maxOf(playbackDuration.toFloat(), 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    appVolume: Float,
    onVolumeChanged: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var memoryUsage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedBytes = runtime.totalMemory() - runtime.freeMemory()
            val usedMegabytes = usedBytes / (1024 * 1024)
            val maxMegabytes = runtime.maxMemory() / (1024 * 1024)
            memoryUsage = "$usedMegabytes MB / $maxMegabytes MB"
            delay(1000)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
            }
            Text(
                text = "Beállítások",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Memóriahasználat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memoryUsage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Alkalmazás hangereje",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(appVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = appVolume,
                        onValueChange = onVolumeChanged,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

    }
}

@Composable
fun SongsListView(
    songs: List<AudioItem>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onSongSelected: (AudioItem) -> Unit,
    onDeleteSong: (AudioItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = songs, key = { it.id }) { song ->
            val isCurrent = currentMediaId == song.id.toString()
            val showPause = isCurrent && isPlaying
            var menuExpanded by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onSongSelected(song) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (showPause) PauseIcon else Icons.Default.PlayArrow,
                                contentDescription = if (showPause) "Szünet" else "Lejátszás",
                                tint = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciók"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Törlés") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteSong(song)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
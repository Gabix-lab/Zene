package me.Gabix.zene

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.Gabix.zene.ui.theme.ZeneTheme

class MainActivity : ComponentActivity() {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var songsState by mutableStateOf<List<AudioItem>>(emptyList())
    private var repeatModeState by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var isShuffleModeState by mutableStateOf(false)
    private var isPlayingState by mutableStateOf(false)
    private var currentMediaIdState by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var observerRegistered = false

    private val sharedPreferences by lazy {
        getSharedPreferences("zene_settings", MODE_PRIVATE)
    }
    private var playbackPositionState by mutableStateOf(0L)
    private var playbackDurationState by mutableStateOf(0L)
    private var appVolumeState by mutableStateOf(1f)

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            loadJob?.cancel()
            loadJob = lifecycleScope.launch {
                delay(500)
                loadAudioFiles()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (audioGranted) {
            loadAudioFiles()
            registerMediaObserver()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appVolumeState = sharedPreferences.getFloat("app_volume", 1f)

        setContent {
            LaunchedEffect(isPlayingState, currentMediaIdState) {
                while (true) {
                    mediaController?.let { controller ->
                        playbackPositionState = controller.currentPosition
                        val d = controller.duration
                        playbackDurationState = if (d > 0) d else 0L
                    }
                    delay(500)
                }
            }

            ZeneTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MusicScreen(
                            songs = songsState,
                            repeatMode = repeatModeState,
                            isShuffleMode = isShuffleModeState,
                            isPlaying = isPlayingState,
                            currentMediaId = currentMediaIdState,
                            playbackPosition = playbackPositionState,
                            playbackDuration = playbackDurationState,
                            appVolume = appVolumeState,
                            onSongSelected = { song, list -> handleSongClick(song, list) },
                            onCycleMode = { cyclePlaybackMode() },
                            onDeleteSong = { song -> deleteSong(song) },
                            onSeekTo = { pos -> mediaController?.seekTo(pos); playbackPositionState = pos },
                            onVolumeChanged = { newVol ->
                                appVolumeState = newVol
                                mediaController?.volume = newVol
                                sharedPreferences.edit().putFloat("app_volume", newVol).apply()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

        startPlaybackService()
        bindMediaController()
        checkAndRequestPermission()
    }

    private fun deleteSong(song: AudioItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = song.contentUri
                val deletedRows = contentResolver.delete(uri, null, null)
                withContext(Dispatchers.Main) {
                    if (deletedRows > 0) {
                        Toast.makeText(this@MainActivity, "Dal sikeresen törölve", Toast.LENGTH_SHORT).show()
                        loadAudioFiles()
                    } else {
                        Toast.makeText(this@MainActivity, "Nem sikerült törölni a fájlt (lehet rendszerfájl)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Nincs jogosultság a fájl törléséhez", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Hiba történt a törlés során", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        startService(intent)
    }

    private fun bindMediaController() {
        if (mediaControllerFuture != null) return

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                val controller = mediaControllerFuture?.get() ?: return@addListener
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        repeatModeState = repeatMode
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        isShuffleModeState = shuffleModeEnabled
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isPlayingState = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentMediaIdState = mediaItem?.mediaId
                    }
                })
                repeatModeState = controller.repeatMode
                isShuffleModeState = controller.shuffleModeEnabled
                isPlayingState = controller.isPlaying
                currentMediaIdState = controller.currentMediaItem?.mediaId
                controller.volume = appVolumeState
            } catch (t: Throwable) {
                Log.e("MainActivity", "MediaController bind failed", t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission()) {
            loadAudioFiles()
            registerMediaObserver()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterMediaObserver()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermission() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            loadAudioFiles()
            registerMediaObserver()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun registerMediaObserver() {
        if (observerRegistered) return
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            false,
            mediaObserver
        )
        observerRegistered = true
    }

    private fun unregisterMediaObserver() {
        if (!observerRegistered) return
        try {
            contentResolver.unregisterContentObserver(mediaObserver)
        } catch (_: Throwable) {
        }
        observerRegistered = false
    }

    private fun loadAudioFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val songs = AudioRepository.fetchAudioFiles(this@MainActivity)
            withContext(Dispatchers.Main) {
                songsState = songs
            }
        }
    }

    private fun handleSongClick(song: AudioItem, currentList: List<AudioItem> = songsState) {
        val controller = mediaController ?: return

        if (currentMediaIdState == song.id.toString()) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            return
        }

        if (currentList.isEmpty()) return

        val mediaItems = currentList.map {
            MediaItem.Builder()
                .setUri(it.contentUri)
                .setMediaId(it.id.toString())
                .build()
        }
        val selectedIndex = currentList.indexOfFirst { it.id == song.id }
        if (selectedIndex == -1) return

        controller.setMediaItems(mediaItems, selectedIndex, 0L)
        controller.prepare()
        controller.play()
    }

    private fun cyclePlaybackMode() {
        val controller = mediaController ?: return
        val command = SessionCommand(PlaybackService.COMMAND_CYCLE_MODE, Bundle.EMPTY)
        controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    override fun onDestroy() {
        unregisterMediaObserver()
        mediaController?.release()
        mediaController = null
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaControllerFuture = null
        super.onDestroy()
    }
}
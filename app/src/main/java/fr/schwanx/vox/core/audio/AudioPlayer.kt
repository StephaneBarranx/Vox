package fr.schwanx.vox.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var player: ExoPlayer? = null
    private var trackingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private fun getOrCreatePlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) {
                            startPositionTracking()
                        } else {
                            stopPositionTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _durationMs.value = duration.coerceAtLeast(0L)
                        } else if (playbackState == Player.STATE_ENDED) {
                            _isPlaying.value = false
                            _currentPositionMs.value = _durationMs.value
                            stopPositionTracking()
                        }
                    }
                })
            }
        }
        return player!!
    }

    fun prepareAndPlay(file: File) {
        prepareAndPlay(Uri.fromFile(file))
    }

    fun prepareAndPlay(uri: Uri) {
        val p = getOrCreatePlayer()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun stop() {
        player?.stop()
        _isPlaying.value = false
        stopPositionTracking()
    }

    fun release() {
        stopPositionTracking()
        player?.release()
        player = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    private fun startPositionTracking() {
        stopPositionTracking()
        trackingJob = scope.launch {
            while (_isPlaying.value) {
                player?.let { p ->
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                }
                delay(100)
            }
        }
    }

    private fun stopPositionTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }
}

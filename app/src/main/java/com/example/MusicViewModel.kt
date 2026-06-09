package com.example

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

// Player states
enum class PlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

// Playback repeating modes
enum class RepeatMode {
    LOOP_LIST,
    LOOP_SINGLE,
    SHUFFLE
}

// Song Track Data Model
data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val coverColorStart: Color,
    val coverColorEnd: Color,
    val isOfflineSynth: Boolean = false,
    val isSpaceDrone: Boolean = false,
    val synthBpm: Int = 80,
    val url: String = "",
    val lyrics: String = "",
    val genre: String = "Ambient"
)

// Lyrics line data helper
data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

class MusicViewModel : ViewModel() {

    // Preset songs list (Offline synths + direct royalty-free URLs)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    // Player States
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _trackDuration = MutableStateFlow(0L)
    val trackDuration: StateFlow<Long> = _trackDuration.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.LOOP_LIST)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _volume = MutableStateFlow(0.8f) // 0f to 1f
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Real-time audio spectrum visualizer (12 bars level data)
    private val _visualizerBars = MutableStateFlow(List(16) { 0.1f })
    val visualizerBars: StateFlow<List<Float>> = _visualizerBars.asStateFlow()

    // Lyrics lines derived state
    private val _parsedLyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val parsedLyrics: StateFlow<List<LyricsLine>> = _parsedLyrics.asStateFlow()

    private val _currentLyricsIndex = MutableStateFlow(-1)
    val currentLyricsIndex: StateFlow<Int> = _currentLyricsIndex.asStateFlow()

    // Media and Synthesis Drivers
    private var mediaPlayer: MediaPlayer? = null
    private var synthEngine: CustomLocalSynth? = null

    // Background ticking jobs
    private var playbackTickerJob: Job? = null
    private var visualizerTickerJob: Job? = null

    init {
        setupPresetTracks()
        // Select the first track by default
        _tracks.value.firstOrNull()?.let {
            selectTrack(it, playImmediately = false)
        }
        startVisualizerTicker()
    }

    private fun setupPresetTracks() {
        val lyricsRain = """
            [00:00] (山野细雨朦胧，风铃轻摇氛围渐起)
            [00:04] 漫步于无人的青石幽径
            [00:09] 清风拂过沙沙作响的翠竹林
            [00:14] 远处空灵的古琴合成音色响起
            [00:19] 如清泉激荡磐石，澄澈无瑕
            [00:24] 拂去心头积攒的主机喧嚣
            [00:29] 此时此刻，唯有松涛与呼吸陪伴
            [00:34] 倚靠朱红长廊，看珠帘卷断细雨
            [00:40] 玄幻合成音符荡漾，顿觉物我两忘
            [00:46] (旋律进入深层延时空灵回音)
            [00:52] 雨声渐渐退却，内心已然安宁如海
            [01:00] (本曲由本地高保真合成器实时发声)
        """.trimIndent()

        val lyricsNebula = """
            [00:00] (深空低频共振，宇宙尘埃泛起涟漪)
            [00:06] 欢迎来到仙女座悬臂外侧
            [00:12] 超弦合成波犹如群星划过天际
            [00:18] 引力波共鸣荡漾在耳边
            [00:24] 抛却地球重力，全身心漂浮于星河
            [00:30] 看着蔚蓝星球在黑暗中静静旋转
            [00:36] 温暖的复古合成器长音将你拥抱
            [00:42] 宇宙本无声，唯心弦在跃动
            [00:49] 穿梭黑暗虫洞，寻找那片寂静绿洲
            [00:56] 光年外的恒星脉冲，指引回家的路
            [01:05] (本地多声部振荡器实时运算中)
        """.trimIndent()

        val lyricsSunsetLeft = """
            [00:00] (落日电子摇摆前奏，Lofi节奏流转)
            [00:08] 沿着蔚蓝海岸公路由东向西疾驰
            [00:14] 电台正哼着温暖而慵懒的曲调
            [00:20] 黄金时刻正慢慢在后视镜里融化
            [00:26] 紫色云彩早已铺满了半片天空
            [00:32] 在这趟宇宙漫游里，烦恼皆可抛去
            [00:38] 随着慵懒的低音，与落日轨迹同步
            [00:44] 生命本是一段绚烂而缓慢的旅程
            [00:50] 在夕阳没入地平线前，尽情沉醉
            [00:56] 晚风吹拂面颊，海潮一遍遍洗刷
            [01:02] 就让我们永远停留在这一刻吧
        """.trimIndent()

        val lyricsWoods = """
            [00:00] (风吹林地，原声木吉他与萨克斯渐入)
            [00:10] 穿过挂满清晨露珠的低语森林
            [00:18] 斑驳的阳光洒在覆满青苔的小径
            [00:26] 树冠下回响着极光与精灵的呢喃
            [00:34] 远离网络轰鸣，脚步不紧不慢
            [00:42] 让双脚在大地生根，让灵魂随风荡漾
            [00:50] 这首自然的协奏曲，只为你我一人演奏
            [00:58] 极光在林梢闪烁，照亮前行道路
            [01:06] 深呼吸，将林木的芬芳印刻在心
        """.trimIndent()

        val list = listOf(
            Track(
                id = 1,
                title = "山雨清风 · 禅意颂钵",
                artist = "算法发声内置合成器",
                coverColorStart = Color(0xFF134E5E),
                coverColorEnd = Color(0xFF71B280),
                isOfflineSynth = true,
                isSpaceDrone = false,
                synthBpm = 64,
                lyrics = lyricsRain,
                genre = "Zen Synth"
            ),
            Track(
                id = 2,
                title = "星空冥想 · 深空共鸣",
                artist = "物理算法太空弦乐",
                coverColorStart = Color(0xFF0F2027),
                coverColorEnd = Color(0xFF203A43),
                isOfflineSynth = true,
                isSpaceDrone = true,
                synthBpm = 50,
                lyrics = lyricsNebula,
                genre = "Cosmic Drone"
            ),
            Track(
                id = 3,
                title = "Sunset Cruise · 慵懒海岸",
                artist = "Lofi Retro Jam",
                coverColorStart = Color(0xFFF3904F),
                coverColorEnd = Color(0xFF3B4371),
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                lyrics = lyricsSunsetLeft,
                genre = "Lofi Beats"
            ),
            Track(
                id = 4,
                title = "Aurora Woods · 晨光林地",
                artist = "Ambient Acoustic",
                coverColorStart = Color(0xFF1D976C),
                coverColorEnd = Color(0xFF93F9B9),
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                lyrics = lyricsWoods,
                genre = "Chill Acoustic"
            ),
            Track(
                id = 5,
                title = "Cafe Chillout · 午后咖啡馆",
                artist = "Sunset Boulevard Ensemble",
                coverColorStart = Color(0xFF8A2387),
                coverColorEnd = Color(0xFFE94057),
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                lyrics = lyricsSunsetLeft.replace("落日", "午后咖啡馆"),
                genre = "Smooth Jazz"
            )
        )
        _tracks.value = list
    }

    fun selectTrack(track: Track, playImmediately: Boolean = true) {
        // Reset previous media if active
        stopAllPlayback()

        _currentTrack.value = track
        parseLyrics(track.lyrics)
        _currentPosition.value = 0L

        if (track.isOfflineSynth) {
            // Synth songs are endless, but we represent them as 3 minutes (180s) on timer bar for neat visual consistency
            _trackDuration.value = 180000L
        } else {
            _trackDuration.value = 0L // Determined dynamically upon load
        }

        if (playImmediately) {
            playActiveTrack()
        } else {
            _playbackState.value = PlaybackState.IDLE
        }
    }

    fun togglePlayPause() {
        if (_playbackState.value == PlaybackState.PLAYING) {
            pauseActiveTrack()
        } else {
            playActiveTrack()
        }
    }

    private fun playActiveTrack() {
        val track = _currentTrack.value ?: return

        if (track.isOfflineSynth) {
            _playbackState.value = PlaybackState.PLAYING
            startSynthPlayback(track)
            startPlaybackTicker()
        } else {
            _playbackState.value = PlaybackState.LOADING
            setupMediaPlayer(track.url)
        }
    }

    private fun pauseActiveTrack() {
        _playbackState.value = PlaybackState.PAUSED
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        synthEngine?.pause()
        playbackTickerJob?.cancel()
    }

    private fun startSynthPlayback(track: Track) {
        if (synthEngine == null) {
            synthEngine = CustomLocalSynth()
        }
        synthEngine?.start(viewModelScope, track.synthBpm, track.isSpaceDrone)
    }

    private fun setupMediaPlayer(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(url)
                    setVolume(_volume.value, _volume.value)
                    
                    setOnPreparedListener { mp ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (_currentTrack.value?.url == url) {
                                _trackDuration.value = mp.duration.toLong()
                                _playbackState.value = PlaybackState.PLAYING
                                mp.start()
                                startPlaybackTicker()
                            } else {
                                mp.release()
                            }
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e("MusicVM", "MediaPlayer Error $what, $extra")
                        viewModelScope.launch(Dispatchers.Main) {
                            _playbackState.value = PlaybackState.ERROR
                        }
                        true
                    }

                    setOnCompletionListener {
                        viewModelScope.launch(Dispatchers.Main) {
                            handleCompletion()
                        }
                    }

                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    _playbackState.value = PlaybackState.ERROR
                }
            }
        }
    }

    private fun handleCompletion() {
        playbackTickerJob?.cancel()
        _currentPosition.value = 0L

        when (_repeatMode.value) {
            RepeatMode.LOOP_SINGLE -> {
                _currentTrack.value?.let { selectTrack(it, playImmediately = true) }
            }
            RepeatMode.LOOP_LIST -> {
                skipNext()
            }
            RepeatMode.SHUFFLE -> {
                playRandomTrack()
            }
        }
    }

    fun skipNext() {
        val list = filteredTracks()
        if (list.isEmpty()) return
        
        val current = _currentTrack.value
        val index = list.indexOfFirst { it.id == current?.id }

        if (_repeatMode.value == RepeatMode.SHUFFLE) {
            playRandomTrack()
        } else {
            val nextIndex = (index + 1) % list.size
            selectTrack(list[nextIndex], playImmediately = true)
        }
    }

    fun skipPrevious() {
        val list = filteredTracks()
        if (list.isEmpty()) return

        val current = _currentTrack.value
        val index = list.indexOfFirst { it.id == current?.id }

        if (index == -1) return
        val prevIndex = if (index - 1 < 0) list.size - 1 else index - 1
        selectTrack(list[prevIndex], playImmediately = true)
    }

    private fun playRandomTrack() {
        val list = filteredTracks()
        if (list.size <= 1) {
            _currentTrack.value?.let { selectTrack(it, playImmediately = true) }
            return
        }
        val currentId = _currentTrack.value?.id ?: -1
        val available = list.filter { it.id != currentId }
        val randomTrack = available.random()
        selectTrack(randomTrack, playImmediately = true)
    }

    fun seekTo(progressMs: Long) {
        val maxDuration = _trackDuration.value
        val boundProgress = progressMs.coerceIn(0L, maxDuration)
        _currentPosition.value = boundProgress

        val track = _currentTrack.value
        if (track != null && !track.isOfflineSynth) {
            mediaPlayer?.seekTo(boundProgress.toInt())
        }
        updateLyricsIndex(boundProgress)
    }

    fun setVolume(volValue: Float) {
        val vol = volValue.coerceIn(0f, 1f)
        _volume.value = vol
        mediaPlayer?.setVolume(vol, vol)
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.LOOP_LIST -> RepeatMode.LOOP_SINGLE
            RepeatMode.LOOP_SINGLE -> RepeatMode.SHUFFLE
            RepeatMode.SHUFFLE -> RepeatMode.LOOP_LIST
        }
    }

    fun toggleFavorite(trackId: Int) {
        val currentFavs = _favorites.value.toMutableSet()
        if (currentFavs.contains(trackId)) {
            currentFavs.remove(trackId)
        } else {
            currentFavs.add(trackId)
        }
        _favorites.value = currentFavs
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun filteredTracks(): List<Track> {
        val q = _searchQuery.value.trim().lowercase()
        val all = _tracks.value
        if (q.isEmpty()) return all
        return all.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.genre.lowercase().contains(q)
        }
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            while (isActive) {
                if (_playbackState.value == PlaybackState.PLAYING) {
                    val track = _currentTrack.value
                    if (track != null) {
                        if (track.isOfflineSynth) {
                            // Offline synthesis just advances time procedurally
                            val nextPos = _currentPosition.value + 1000
                            _currentPosition.value = if (nextPos >= _trackDuration.value) 0L else nextPos
                        } else {
                            mediaPlayer?.let {
                                if (it.isPlaying) {
                                    _currentPosition.value = it.currentPosition.toLong()
                                }
                            }
                        }
                        updateLyricsIndex(_currentPosition.value)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun startVisualizerTicker() {
        visualizerTickerJob?.cancel()
        visualizerTickerJob = viewModelScope.launch {
            while (isActive) {
                if (_playbackState.value == PlaybackState.PLAYING) {
                    // Refresh visualizer with energetic movement depending on active song tempo
                    val track = _currentTrack.value
                    val mult = if (track?.isOfflineSynth == true) 0.6f else 1.0f
                    
                    _visualizerBars.value = List(16) { index ->
                        // Generate waves mathematically based on system clock + index offsets
                        val wave = (sin(System.currentTimeMillis() / 150.0 + index * 0.9) * 0.35 + 0.5).toFloat()
                        val randomSpike = (Math.random() * 0.25).toFloat()
                        (wave + randomSpike).coerceIn(0.08f, 1.0f) * mult
                    }
                } else if (_playbackState.value == PlaybackState.LOADING) {
                    // Soft shifting loading wave
                    _visualizerBars.value = List(16) { index ->
                        val wave = (sin(System.currentTimeMillis() / 300.0 + index * 0.5) * 0.15 + 0.2).toFloat()
                        wave.coerceIn(0.05f, 1.0f)
                    }
                } else {
                    // Decay spectrum to standard silent heights
                    _visualizerBars.value = _visualizerBars.value.map { it * 0.85f }.map { if (it < 0.05f) 0.04f else it }
                }
                delay(80)
            }
        }
    }

    private fun parseLyrics(lrcText: String) {
        val lines = mutableListOf<LyricsLine>()
        if (lrcText.isEmpty()) {
            _parsedLyrics.value = emptyList()
            return
        }

        try {
            lrcText.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("[") && line.contains("]")) {
                    val parts = line.split("]", limit = 2)
                    if (parts.size == 2) {
                        val timeStr = parts[0].substring(1).trim() // skip "["
                        val text = parts[1].trim()
                        
                        // timeStr format usually is "mm:ss" or "mm:ss.SS"
                        val timeParts = timeStr.split(":")
                        if (timeParts.size == 2) {
                            val mm = timeParts[0].toLongOrNull() ?: 0L
                            val ssParts = timeParts[1].split(".")
                            val ss = ssParts[0].toLongOrNull() ?: 0L
                            val ms = if (ssParts.size == 2) (ssParts[1].toLongOrNull() ?: 0L) * 10 else 0L
                            
                            val totalMs = (mm * 60 + ss) * 1000 + ms
                            lines.add(LyricsLine(totalMs, text))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _parsedLyrics.value = lines.sortedBy { it.timestampMs }
        _currentLyricsIndex.value = -1
    }

    private fun updateLyricsIndex(posMs: Long) {
        val lines = _parsedLyrics.value
        if (lines.isEmpty()) {
            _currentLyricsIndex.value = -1
            return
        }

        var activeIndex = -1
        for (i in lines.indices) {
            if (posMs >= lines[i].timestampMs) {
                activeIndex = i
            } else {
                break
            }
        }
        _currentLyricsIndex.value = activeIndex
    }

    private fun stopAllPlayback() {
        playbackTickerJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        synthEngine?.stop()
        synthEngine = null
    }

    override fun onCleared() {
        stopAllPlayback()
        visualizerTickerJob?.cancel()
        super.onCleared()
    }
}

// Custom Local Ambient Synthesizer using AudioTrack
class CustomLocalSynth {
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private var isPlaying = false

    fun start(scope: CoroutineScope, bpm: Int, isSpaceDrone: Boolean) {
        if (isPlaying) return
        isPlaying = true

        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("SynthEngine", "Failed to start AudioTrack", e)
            return
        }

        synthJob = scope.launch(Dispatchers.Default) {
            // Pentatonic zen bells scale (Chimes): G4, A4, C5, D5, E5, G5, A5
            val chimeScale = doubleArrayOf(
                392.00, 440.00, 523.25, 587.33, 659.25, 783.99, 880.00
            )

            // Multi-frequency drone chord waves (low hums)
            val droneChords = if (isSpaceDrone) {
                // Nebula ambient frequencies - slow, cosmic
                listOf(
                    doubleArrayOf(73.42, 110.0, 146.83), // D2, A2, D3
                    doubleArrayOf(65.41, 98.0, 130.81),  // C2, G2, C3
                    doubleArrayOf(55.00, 82.41, 110.0),  // A1, E2, A2
                    doubleArrayOf(65.41, 98.0, 196.00)   // C2, G2, G3
                )
            } else {
                // Zen green forest chords - warm, woodwind-like
                listOf(
                    doubleArrayOf(130.81, 196.00, 261.63), // C3 + G3 + C4
                    doubleArrayOf(174.61, 220.00, 349.23), // F3 + A3 + F4
                    doubleArrayOf(110.00, 165.00, 220.00), // A2 + E3 + A3
                    doubleArrayOf(146.83, 196.00, 293.66)  // D3 + G3 + D4
                )
            }

            var currentChordIndex = 0
            var sampleCounter = 0
            val beatDurationMs = (60000 / bpm).toLong()
            val samplesPerBeat = (sampleRate * (beatDurationMs / 1000.0)).toInt()

            var activeBellFreq = 0.0
            var bellStartSample = 0
            var bellLengthSamples = 0

            while (isPlaying) {
                val bufferLimit = 512
                val buffer = ShortArray(bufferLimit)
                val activeChord = droneChords[currentChordIndex % droneChords.size]

                for (idx in 0 until bufferLimit) {
                    val globalSample = sampleCounter + idx

                    // Beat triggers chord migrations and potential bell chime strikes
                    if (globalSample % samplesPerBeat == 0) {
                        // Slowly alternate chords on certain beats
                        if (Math.random() < 0.4) {
                            currentChordIndex++
                        }

                        // Sparkle bell chime sound
                        if (Math.random() < 0.65) {
                            activeBellFreq = chimeScale[(Math.random() * chimeScale.size).toInt()]
                            bellStartSample = globalSample
                            bellLengthSamples = (sampleRate * (1.8 + Math.random() * 2.2)).toInt() // 2-4 seconds decay
                        }
                    }

                    // Layer 1: Ambient Background Pad (Drone hums)
                    var pads = 0.0
                    for (f in activeChord) {
                        pads += sin(2.0 * Math.PI * f * globalSample / sampleRate)
                    }
                    pads /= activeChord.size // Normalize
                    pads *= 0.16 // soft volume background drone

                    // Layer 2: Crystal Zen Chime (Bells)
                    var bell = 0.0
                    if (activeBellFreq > 0.0) {
                        val bellElapsed = globalSample - bellStartSample
                        if (bellElapsed < bellLengthSamples) {
                            val decay = 1.0 - (bellElapsed.toDouble() / bellLengthSamples)
                            val env = decay * decay * decay * decay // aggressive smooth bell curves
                            
                            bell += sin(2.0 * Math.PI * activeBellFreq * bellElapsed / sampleRate)
                            bell += 0.4 * sin(2.0 * Math.PI * (activeBellFreq * 2.01) * bellElapsed / sampleRate) // disharmonic octave
                            bell += 0.15 * sin(2.0 * Math.PI * (activeBellFreq * 3.0) * bellElapsed / sampleRate)  // chime overtone
                            bell /= 1.55
                            bell *= env * 0.32 // chime prominence
                        } else {
                            activeBellFreq = 0.0
                        }
                    }

                    // Layer 3: Environment textures (Analog vinyl dust or dynamic ocean hums)
                    val soundscapes = if (!isSpaceDrone) {
                        // Nature forest rustles using random walk crackle
                        (Math.random() * 2.0 - 1.0) * 0.012
                    } else {
                        // Deep stellar space sweep noise
                        sin(2.0 * Math.PI * 0.15 * globalSample / sampleRate) * ((Math.random() - 0.5) * 0.006)
                    }

                    // Combine audios
                    val masterMix = pads + bell + soundscapes
                    val limitedMix = masterMix.coerceIn(-1.0, 1.0)
                    buffer[idx] = (limitedMix * 32767.0).toInt().toShort()
                }

                // Push values onto the audio hardware track channels
                audioTrack?.write(buffer, 0, bufferLimit)
                sampleCounter += bufferLimit
                
                // Allow CPU relaxation
                delay(4)
            }
        }
    }

    fun pause() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}

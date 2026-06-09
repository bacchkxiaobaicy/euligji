package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MainMusicAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainMusicAppScreen(viewModel: MusicViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val trackDuration by viewModel.trackDuration.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val visualizerBars by viewModel.visualizerBars.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("Square") } // "Square" or "Player"
    var showPlayerLyrics by remember { mutableStateOf(false) } // Toggle center vinyl with lyrics

    val context = LocalContext.current

    // Error toast feedback
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.ERROR) {
            Toast.makeText(
                context,
                "播放出错，可能网络连接受限。请尝试顶部算法内置「本地合成器」曲目进行离线听歌！",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Dynamic background brush from current active song profile colors
    val themeColorStart = currentTrack?.coverColorStart ?: Color(0xFF1F1C2C)
    val themeColorEnd = currentTrack?.coverColorEnd ?: Color(0xFF928DAB)

    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            themeColorStart.copy(alpha = 0.28f),
            themeColorEnd.copy(alpha = 0.12f),
            Color(0xFF0D0D14)
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 2000f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                // Sleek Header Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SOUNDSCAPE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (activeTab == "Square") "音乐广场" else "正在播放",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        // Floating badge for active synthesizer or MP3 play state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            val activeGenre = currentTrack?.genre ?: "Zen Synth"
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (playbackState == PlaybackState.PLAYING) Color(0xFF00FFCC) else Color.Gray,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeGenre,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // Navigation Rails & Mini-Player
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                            )
                        )
                ) {
                    // Mini player floating bar when on Library screen
                    AnimatedVisibility(
                        visible = activeTab == "Square" && currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentTrack?.let { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .clickable { activeTab = "Player" }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Glassy disc preview icon
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(track.coverColorStart, track.coverColorEnd)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = track.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Play Mini action
                                    IconButton(
                                        onClick = { viewModel.togglePlayPause() },
                                        modifier = Modifier.testTag("mini_play_pause_button")
                                    ) {
                                        val icon = if (playbackState == PlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "播放暂停",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { viewModel.skipNext() },
                                        modifier = Modifier.testTag("mini_next_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = "下一首",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tidy system bars
                    Spacer(modifier = Modifier.height(4.dp))

                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "Square",
                            onClick = { activeTab = "Square" },
                            label = { Text("音乐广场", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == "Square") Icons.Filled.List else Icons.Filled.List,
                                    contentDescription = "广场",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00FFCC),
                                selectedTextColor = Color(0xFF00FFCC),
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                indicatorColor = Color.White.copy(alpha = 0.08f)
                            )
                        )

                        NavigationBarItem(
                            selected = activeTab == "Player",
                            onClick = { activeTab = "Player" },
                            label = { Text("正在播放", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == "Player") Icons.Filled.PlayCircle else Icons.Filled.PlayCircle,
                                    contentDescription = "播放器",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00FFCC),
                                selectedTextColor = Color(0xFF00FFCC),
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                indicatorColor = Color.White.copy(alpha = 0.08f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState == "Player") {
                            (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()).togetherWith(
                                slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
                            )
                        } else {
                            (slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn()).togetherWith(
                                slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                            )
                        }
                    },
                    label = "tab_fade"
                ) { targetTab ->
                    when (targetTab) {
                        "Square" -> MusicSquarePane(
                            viewModel = viewModel,
                            tracks = viewModel.filteredTracks(),
                            searchQuery = searchQuery,
                            favorites = favorites,
                            currentTrack = currentTrack,
                            playbackState = playbackState,
                            onPlayTrack = { track ->
                                viewModel.selectTrack(track)
                                activeTab = "Player"
                            }
                        )

                        "Player" -> PlayerDashboardPane(
                            viewModel = viewModel,
                            currentTrack = currentTrack,
                            playbackState = playbackState,
                            currentPosition = currentPosition,
                            trackDuration = trackDuration,
                            repeatMode = repeatMode,
                            volume = volume,
                            favorites = favorites,
                            visualizerBars = visualizerBars,
                            showLyrics = showPlayerLyrics,
                            onToggleLyrics = { showPlayerLyrics = !showPlayerLyrics }
                        )
                    }
                }
            }
        }
    }
}

// ---------------- MUSIC SQUARE TAB ----------------
@Composable
fun MusicSquarePane(
    viewModel: MusicViewModel,
    tracks: List<Track>,
    searchQuery: String,
    favorites: Set<Int>,
    currentTrack: Track?,
    playbackState: PlaybackState,
    onPlayTrack: (Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Modern glass search panel
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("搜索曲目、合奏家、流派风格...", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("search_field"),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "清空",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                focusedBorderColor = Color(0xFF00FFCC).copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Intro offline synthesizer invitation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF134E5E).copy(alpha = 0.7f), Color(0xFF71B280).copy(alpha = 0.4f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Hearing,
                        contentDescription = "合成器",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "算法发声本地合成器 (免流量)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "前两首音乐源自本地物理发音引擎：每次点击都会在后台利用音响组件即时算出舒缓、助眠的五声部鸣钟波源，提供最纯粹的自然之音。",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }

        // Search empty state
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.AudioFile,
                        contentDescription = "空白",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "未搜索到匹配音频曲目",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(tracks) { idx, track ->
                    val isPlayingThis = currentTrack?.id == track.id
                    val isFavorite = favorites.contains(track.id)

                    // Card block
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isPlayingThis) Color.White.copy(alpha = 0.1f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPlayingThis) Color(0xFF00FFCC).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onPlayTrack(track) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Beautiful cover thumbnail disc
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(track.coverColorStart, track.coverColorEnd)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPlayingThis && playbackState == PlaybackState.PLAYING) {
                                    // Animated sound spectrum badge
                                    MiniEqIcon()
                                } else {
                                    Icon(
                                        imageVector = if (track.isOfflineSynth) Icons.Filled.Memory else Icons.Filled.CloudQueue,
                                        contentDescription = "类型",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = track.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlayingThis) Color(0xFF00FFCC) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Favorite toggle button
                            IconButton(
                                onClick = { viewModel.toggleFavorite(track.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "加收藏",
                                    tint = if (isFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Quick selection arrow indicator
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "进入播放",
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- NOW PLAYING TAB ----------------
@Composable
fun PlayerDashboardPane(
    viewModel: MusicViewModel,
    currentTrack: Track?,
    playbackState: PlaybackState,
    currentPosition: Long,
    trackDuration: Long,
    repeatMode: RepeatMode,
    volume: Float,
    favorites: Set<Int>,
    visualizerBars: List<Float>,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit
) {
    if (currentTrack == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.MusicOff,
                    contentDescription = "暂无音频",
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "请先在「音乐广场」选择音频曲目",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    val isFavorite = favorites.contains(currentTrack.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Track Header (Current Selection Title/Artist)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTrack.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentTrack.artist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Center visual deck (either Rotating record CD, or Scrolling synced lyrics)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350)).togetherWith(fadeOut(animationSpec = tween(350)))
                },
                label = "lyric_crossfade"
            ) { showLyricSheet ->
                if (showLyricSheet) {
                    PlayerLyricsView(viewModel = viewModel, currentPosition = currentPosition)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Beautiful Analog Rotating Vinyl Record
                        RotatingVinylPlate(
                            coverColorStart = currentTrack.coverColorStart,
                            coverColorEnd = currentTrack.coverColorEnd,
                            isPlayState = playbackState == PlaybackState.PLAYING
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Holographic audio spectrum waves below Vinyl player
                        PulseAudioVisualizerBars(bars = visualizerBars)
                    }
                }
            }
        }

        // Playing Controller Console Group (Slider, Buttons, Volume)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Scrub timeline bar
            val currentProgressRatio = if (trackDuration > 0) currentPosition.toFloat() / trackDuration else 0f
            var localSliderState by remember(currentPosition) { mutableStateOf(currentProgressRatio) }
            var isScrubbing by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimeline(currentPosition),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Slider(
                    value = if (isScrubbing) localSliderState else currentProgressRatio,
                    onValueChange = {
                        isScrubbing = true
                        localSliderState = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        val targetMs = (localSliderState * trackDuration).toLong()
                        viewModel.seekTo(targetMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("progress_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FFCC),
                        activeTrackColor = Color(0xFF00FFCC),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    )
                )

                Text(
                    text = formatTimeline(trackDuration),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Controller console buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Mode Selector (Shuffle / Single / List)
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val icon = when (repeatMode) {
                        RepeatMode.LOOP_LIST -> Icons.Filled.Repeat
                        RepeatMode.LOOP_SINGLE -> Icons.Filled.RepeatOne
                        RepeatMode.SHUFFLE -> Icons.Filled.Shuffle
                    }
                    val iconTint = if (repeatMode == RepeatMode.LOOP_LIST) Color.White.copy(alpha = 0.5f) else Color(0xFF00FFCC)
                    Icon(imageVector = icon, contentDescription = "循环模式", tint = iconTint)
                }

                // Previous track
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Circle Action Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00FFCC), Color(0xFF00BFFF))
                            )
                        )
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState == PlaybackState.LOADING) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        val icon = if (playbackState == PlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow
                        Icon(
                            imageVector = icon,
                            contentDescription = "播放暂停",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Next track
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Center View toggler (Lyrics alternate)
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        imageVector = if (showLyrics) Icons.Filled.FilePresent else Icons.Filled.Lyrics,
                        contentDescription = "切换歌词",
                        tint = if (showLyrics) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Neat volume slider bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeMute,
                    contentDescription = "静音",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )

                Slider(
                    value = volume,
                    onValueChange = { viewModel.setVolume(it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = 10.dp)
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.8f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    )
                )

                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "最大音量",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------------- VINYL Rotating visual components ----------------
@Composable
fun RotatingVinylPlate(
    coverColorStart: Color,
    coverColorEnd: Color,
    isPlayState: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Current angle state animation
    val activeAngle = if (isPlayState) rotationAngle else 0f

    // Needle rotation mapping (tonearm rotation moves onto record when music starts)
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlayState) 22f else 3f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tonearm"
    )

    Box(
        modifier = Modifier
            .size(280.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Vinyl Disc Plate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(activeAngle)
                .clip(CircleShape)
                .background(Color(0xFF0F0F13))
                .border(6.dp, Color(0xFF1C1C24), CircleShape)
        ) {
            // Glossy Concentric Groove vinyl rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radiusMax = size.width / 2
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = radiusMax * 0.85f,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = radiusMax * 0.7f,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = radiusMax * 0.55f,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // Rich Radial Colorful Label disk center cover
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(coverColorStart, coverColorEnd)
                        )
                    )
                    .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
            ) {
                // Absolute CD center physical spindle aperture hole
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                )
            }
        }

        // Silver retro magnetic needle tonearm overlay at the top-right
        Canvas(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-20).dp)
                .graphicsLayer {
                    rotationZ = tonearmAngle
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.8f, 0.2f)
                }
        ) {
            val pivotX = size.width * 0.8f
            val pivotY = size.height * 0.2f

            // Top mount base
            drawCircle(color = Color(0xFFE2E8F0), radius = 8.dp.toPx(), center = Offset(pivotX, pivotY))
            drawCircle(color = Color(0xFF475569), radius = 4.dp.toPx(), center = Offset(pivotX, pivotY))

            // Long tone-arm stick
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(pivotX, pivotY),
                end = Offset(size.width * 0.25f, size.height * 0.85f),
                strokeWidth = 3.dp.toPx()
            )

            // Bent head cartridge
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(size.width * 0.25f, size.height * 0.85f),
                end = Offset(size.width * 0.12f, size.height * 0.95f),
                strokeWidth = 4.dp.toPx()
            )

            // Needle cartridge weight head block
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(size.width * 0.08f, size.height * 0.90f),
                size = Size(10.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

// ---------------- WAVE SPECTRUM BARS VISUALIZER ----------------
@Composable
fun PulseAudioVisualizerBars(bars: List<Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { heightRatio ->
            // Dynamic anim height values
            val animHeight by animateFloatAsState(
                targetValue = heightRatio,
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                label = "bars"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(animHeight.coerceIn(0.12f, 1.0f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF00FFCC), Color(0xFF00BFFF).copy(alpha = 0.4f))
                        )
                    )
            )
        }
    }
}

// ---------------- SCROLLING LYRICS LIST PANE ----------------
@Composable
fun PlayerLyricsView(
    viewModel: MusicViewModel,
    currentPosition: Long
) {
    val lyricsLines by viewModel.parsedLyrics.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentLyricsIndex.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    // Smooth scroll list on index updating
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyricsLines.size) {
            // Maintain centered alignment
            lazyListState.animateScrollToItem(index = currentIndex, scrollOffset = -220)
        }
    }

    if (lyricsLines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "纯乐器曲目，暂无匹配歌词文字",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .testTag("lyrics_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 140.dp)
    ) {
        itemsIndexed(lyricsLines) { idx, line ->
            val isActive = idx == currentIndex

            val fontSizeAnimation by animateFloatAsState(
                targetValue = if (isActive) 16.sp.value else 13.sp.value,
                animationSpec = spring(),
                label = "lyric_size"
            )
            val alphaAnimation by animateFloatAsState(
                targetValue = if (isActive) 1.0f else 0.4f,
                animationSpec = spring(),
                label = "lyric_alpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.seekTo(line.timestampMs) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00FFCC), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = line.text,
                        fontSize = fontSizeAnimation.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isActive) Color(0xFF00FFCC) else Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .alpha(alphaAnimation)
                    )
                }
            }
        }
    }
}

// Mini dynamic animated sound EQ indicator
@Composable
fun MiniEqIcon() {
    val barValues = listOf(0.4f, 0.9f, 0.6f)
    Row(
        modifier = Modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barValues.forEachIndexed { i, startHeight ->
            val infiniteTransition = rememberInfiniteTransition(label = "eq_$i")
            val heightScale by infiniteTransition.animateFloat(
                initialValue = startHeight,
                targetValue = 0.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + i * 200, easing = LinearOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "barsScale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightScale)
                    .background(Color(0xFF00FFCC), RoundedCornerShape(1.dp))
            )
        }
    }
}

// ---------------- MATH AND STRING FORMATTERS ----------------
fun formatTimeline(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

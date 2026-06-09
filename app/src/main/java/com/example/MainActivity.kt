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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.window.Dialog
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

    val currentThemeId by viewModel.currentThemeId.collectAsStateWithLifecycle()
    val customScripts by viewModel.customScripts.collectAsStateWithLifecycle()
    val selectedSearchSource by viewModel.selectedSearchSource.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("Search") } // "Search", "List", "Settings"
    var showPlayerLyrics by remember { mutableStateOf(false) } // Toggle vinyl center disc with scrolling text
    var isPlayerExpanded by remember { mutableStateOf(false) } // Expand full-screen vinyl player

    val context = LocalContext.current
    val activeTheme = appThemes.find { it.id == currentThemeId } ?: appThemes.first()
    val accentColor = activeTheme.accentColor

    // Restore saved settings, custom scripts and custom sources on launch
    LaunchedEffect(Unit) {
        viewModel.loadAllPersistentData(context)
    }

    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.ERROR) {
            Toast.makeText(
                context,
                "音频加载超时，可能受到物理网络限制。请在「设置」导入其他 JSON 源，或体验离线发音内置算法曲目！",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Dynamic linear blending gradients behind glassy elements
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            activeTheme.backgroundColorStart.copy(alpha = 0.42f),
            activeTheme.backgroundColorEnd.copy(alpha = 0.18f),
            Color(0xFF090A0E)
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
                // Main Header Title Area
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
                                text = "洛雪音乐 • 极客重塑版",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = accentColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (activeTab) {
                                    "Search" -> "搜索音乐"
                                    "List" -> "多源歌单"
                                    "Settings" -> "音源与主题"
                                    else -> "洛雪音乐"
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Floating dynamic badge stating active audio engine tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (playbackState == PlaybackState.PLAYING) accentColor else Color.Gray,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentTrack?.genre ?: "内置合成",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // Bottom Floating Control Deck & Navigation Tab Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black)
                            )
                        )
                ) {
                    // Small floating Mini-Player visible when player window is docked / folded down
                    AnimatedVisibility(
                        visible = !isPlayerExpanded && currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentTrack?.let { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .clickable { isPlayerExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
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
                                    IconButton(
                                        onClick = { viewModel.togglePlayPause() },
                                        modifier = Modifier.testTag("mini_play_pause_button")
                                    ) {
                                        val icon = if (playbackState == PlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "播放暂停",
                                            tint = accentColor,
                                            modifier = Modifier.size(26.dp)
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Classic Tab Swapper Bottom Row
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "Search",
                            onClick = { activeTab = "Search" },
                            label = { Text("聚合搜索", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "搜索",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.White.copy(alpha = 0.08f)
                            )
                        )

                        NavigationBarItem(
                            selected = activeTab == "List",
                            onClick = { activeTab = "List" },
                            label = { Text("我的歌单", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = "歌单",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color.White.copy(alpha = 0.08f)
                            )
                        )

                        NavigationBarItem(
                            selected = activeTab == "Settings",
                            onClick = { activeTab = "Settings" },
                            label = { Text("设置自定义", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "设置",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
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
                        fadeIn(animationSpec = tween(220)).togetherWith(fadeOut(animationSpec = tween(220)))
                    },
                    label = "tab_swapper"
                ) { targetTab ->
                    when (targetTab) {
                        "Search" -> MusicSearchPane(
                            viewModel = viewModel,
                            searchQuery = searchQuery,
                            currentTrack = currentTrack,
                            playbackState = playbackState,
                            accentColor = accentColor
                        )
                        "List" -> MusicListPane(
                            viewModel = viewModel,
                            favorites = favorites,
                            currentTrack = currentTrack,
                            playbackState = playbackState,
                            accentColor = accentColor
                        )
                        "Settings" -> MusicSettingsPane(
                            viewModel = viewModel,
                            currentThemeId = currentThemeId,
                            customScripts = customScripts,
                            sources = sources,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }

        // Fullscreen slide up player view with vinyl CD disc record & scrolling lyrics
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val playerBgGrad = Brush.verticalGradient(
                colors = listOf(
                    (currentTrack?.coverColorStart ?: Color(0xFF134E5E)).copy(alpha = 0.95f),
                    (currentTrack?.coverColorEnd ?: Color(0xFF120B0F)).copy(alpha = 0.97f),
                    Color(0xFF08090C)
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(playerBgGrad)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with close chevron
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isPlayerExpanded = false }) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "收起",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentTrack?.title ?: "未知音轨",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrack?.artist ?: "未知歌手",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { currentTrack?.let { viewModel.toggleFavorite(it.id) } }) {
                            val isFav = currentTrack?.let { favorites.contains(it.id) } ?: false
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (isFav) Color(0xFFFF5252) else Color.White
                            )
                        }
                    }

                    // Middle rotating analog plate CD container or Lyrics sheet view
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable { showPlayerLyrics = !showPlayerLyrics },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = showPlayerLyrics,
                            transitionSpec = {
                                fadeIn().togetherWith(fadeOut())
                            },
                            label = "slide_lyrics"
                        ) { showLyrics ->
                            if (showLyrics) {
                                PlayerLyricsView(viewModel = viewModel, currentPosition = currentPosition, accentColor = accentColor)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RotatingVinylPlate(
                                        coverColorStart = currentTrack?.coverColorStart ?: Color.Gray,
                                        coverColorEnd = currentTrack?.coverColorEnd ?: Color.LightGray,
                                        isPlayState = playbackState == PlaybackState.PLAYING
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    PulseAudioVisualizerBars(bars = visualizerBars, accentColor = accentColor)
                                }
                            }
                        }
                    }

                    // Scrubber panel slider with repeat states & controllers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimeline(currentPosition),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )

                            // Scrubber seekbar
                            Slider(
                                value = if (trackDuration > 0) currentPosition.toFloat() else 0f,
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..(if (trackDuration > 0) trackDuration.toFloat() else 100f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .testTag("playback_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                )
                            )

                            Text(
                                text = formatTimeline(trackDuration),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Console buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Loop state
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                val (icon, desc) = when (repeatMode) {
                                    RepeatMode.LOOP_LIST -> Icons.Filled.Repeat to "列表循环"
                                    RepeatMode.LOOP_SINGLE -> Icons.Filled.RepeatOne to "单曲循环"
                                    RepeatMode.SHUFFLE -> Icons.Filled.Shuffle to "随机播放"
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = desc,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = { viewModel.skipPrevious() }) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "上一首",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            // Big glass play button
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                                    .clickable { viewModel.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playbackState == PlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "播放",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            IconButton(onClick = { viewModel.skipNext() }) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "下一首",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            // Sound levels overlay toggle Volume Indicator
                            IconButton(onClick = {
                                val nextVol = if (volume > 0.1f) 0f else 0.8f
                                viewModel.setVolume(nextVol)
                            }) {
                                Icon(
                                    imageVector = if (volume > 0.05f) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                                    contentDescription = "音量",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 1. MUSIC SEARCH PANE (AGGREGATE MULTI-SOURCE SEARCH) ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSearchPane(
    viewModel: MusicViewModel,
    searchQuery: String,
    currentTrack: Track?,
    playbackState: PlaybackState,
    accentColor: Color
) {
    val selectedSearchSource by viewModel.selectedSearchSource.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val tracks = viewModel.filteredTracks()

    val sourceOptions = listOf(
        "all" to "全部聚合",
        "netease" to "网易云",
        "qq" to "QQ音乐",
        "kugou" to "酷狗",
        "kuwo" to "酷我",
        "migu" to "咪咕",
        "custom" to "外部源"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Modern translucent rounded input box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("搜索周杰伦、晴天、歌手风格或外部源...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("search_field"),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = accentColor
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "清除",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                focusedBorderColor = accentColor.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Horizontal Row of Source Filtering Tabs (Matches direct LX Music desktop style!)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sourceOptions) { (id, label) ->
                val isSelected = selectedSearchSource == id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(30.dp)
                        )
                        .clickable {
                            viewModel.setSelectedSearchSource(id)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Search empty suggestions state or query outputs list
        if (searchQuery.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "洛雪",
                    tint = accentColor.copy(alpha = 0.25f),
                    modifier = Modifier.size(68.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "任意搜索 体验极速多源试听",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Clicking these pills will autocomplete search!
                Text(
                    text = "热门精品搜索：",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val quickTags = listOf("周杰伦", "陈奕迅", "薛之谦", "起风了", "晴天", "素颜")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    maxItemsInEachRow = 3
                ) {
                    quickTags.forEach { keyword ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { viewModel.setSearchQuery(keyword) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = keyword,
                                fontSize = 11.sp,
                                color = accentColor.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            // Searched Tracks List Rendering
            Text(
                text = "查找到【$selectedSearchSource】匹配项 (${tracks.size})",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未检索到匹配结果", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(tracks) { index, track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        val isFav = favorites.contains(track.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPlayingThis) Color.White.copy(alpha = 0.08f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isPlayingThis) accentColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectTrack(track) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic index / EQ visualizer
                                if (isPlayingThis && playbackState == PlaybackState.PLAYING) {
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        MiniEqIcon(accentColor = accentColor)
                                    }
                                } else {
                                    Text(
                                        text = String.format("%02d", index + 1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.width(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlayingThis) accentColor else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = track.genre.replace("特色", "").replace("特选", ""),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = accentColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = track.artist,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        tint = if (isFav) Color(0xFFFF5252) else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp),
                                        contentDescription = "收藏"
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(18.dp),
                                    contentDescription = "试听"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 2. MUSIC MY PLAYLISTS PANE ----------------
@Composable
fun MusicListPane(
    viewModel: MusicViewModel,
    favorites: Set<Int>,
    currentTrack: Track?,
    playbackState: PlaybackState,
    accentColor: Color
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("favorites") } // "all", "favorites", "synthesizer"

    val categoryTracks = when (selectedCategory) {
        "favorites" -> tracks.filter { favorites.contains(it.id) }
        "synthesizer" -> tracks.filter { it.isOfflineSynth }
        else -> tracks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Horizontal tabs to segment lists
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf(
                "favorites" to "我的收藏 💖 (${tracks.count { favorites.contains(it.id) }})",
                "synthesizer" to "本地合成内置 🤖",
                "all" to "全部已导入 📁 (${tracks.size})"
            )

            categories.forEach { (id, label) ->
                val isActive = selectedCategory == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.03f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) accentColor else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedCategory = id }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) accentColor else Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (categoryTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "空白",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (selectedCategory == "favorites") "暂无收藏，快去【聚合搜索】红心试听吧！" else "列表为空",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(categoryTracks) { i, track ->
                    val isPlayingThis = currentTrack?.id == track.id
                    val isFav = favorites.contains(track.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPlayingThis) Color.White.copy(alpha = 0.08f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPlayingThis) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectTrack(track) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPlayingThis && playbackState == PlaybackState.PLAYING) {
                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                MiniEqIcon(accentColor = accentColor)
                            }
                        } else {
                            Text(
                                text = String.format("%02d", i + 1),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlayingThis) accentColor else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.genre} • ${track.artist}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) Color(0xFFFF5252) else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 3. SYSTEM CONFIGS & CUSTOM SCRIPT PANEL ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSettingsPane(
    viewModel: MusicViewModel,
    currentThemeId: String,
    customScripts: List<CustomScript>,
    sources: List<MusicSource>,
    accentColor: Color
) {
    val context = LocalContext.current
    var scriptUrlInput by remember { mutableStateOf("") }
    var neteaseIdInput by remember { mutableStateOf("") }

    var feedbackMsg by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Part A: Dynamic rainbow palettes (Choose application styling theme)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Palette, contentDescription = "主题", tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("极客主题配色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Theme color round configurations selection grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    appThemes.forEach { theme ->
                        val isSelected = currentThemeId == theme.id
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.selectTheme(context, theme.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "已选",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Part B: Import Custom LX JS Script URLs
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Code, contentDescription = "JS", tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入 LX 自定义 JS 音源脚本", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "在此贴入标准洛雪自定义 JS 链接。若导入海棠源(http://music.haitangw.net/cqapi/wv.js)，系统将智能解析并释放多条经典无损多功能音质流！",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = scriptUrlInput,
                        onValueChange = { scriptUrlInput = it },
                        placeholder = { Text("wv.js 或外部自定义口令...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (scriptUrlInput.trim().isEmpty()) {
                                feedbackMsg = "请输入脚本链接。"
                                return@Button
                            }
                            isOperating = true
                            feedbackMsg = "正在提取外部脚本信息并解码..."
                            viewModel.addCustomScript(context, scriptUrlInput.trim()) { success, msg ->
                                isOperating = false
                                feedbackMsg = msg
                                if (success) {
                                    scriptUrlInput = ""
                                }
                            }
                        },
                        enabled = !isOperating,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("导入", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Part C: List imported scripts
        item {
            Column {
                Text(
                    text = "已注册音源脚本 (${customScripts.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (customScripts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.01f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("无已录入的外部 JS 脚本。请从上方复制导入！", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }

        items(customScripts) { script ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(script.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("v${script.version}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(script.description, fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("作者: ${script.author} • 地址: ${script.url}", fontSize = 8.sp, color = Color.White.copy(alpha = 0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = script.isActive,
                        onCheckedChange = { viewModel.toggleScriptActive(context, script.url) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier.scale(0.7f)
                    )

                    IconButton(onClick = { viewModel.deleteCustomScript(context, script.url) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Part D: NetEase ID playlist syncing module
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.LibraryMusic, contentDescription = "歌单", tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拉取同步网易云歌单", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("拉取公开网易歌单，可在上方列表内分流播放：", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = neteaseIdInput,
                        onValueChange = { neteaseIdInput = it },
                        placeholder = { Text("例如: 1954326500...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (neteaseIdInput.trim().isEmpty()) {
                                feedbackMsg = "请输入歌单ID"
                                return@Button
                            }
                            isOperating = true
                            feedbackMsg = "连接网易云节点中并抓取歌曲项..."
                            val source = MusicSource(neteaseIdInput.trim(), "网易歌单", "netease")
                            viewModel.syncMusicSource(context, source) { success, msg ->
                                isOperating = false
                                feedbackMsg = msg
                                if (success) {
                                    neteaseIdInput = ""
                                }
                            }
                        },
                        enabled = !isOperating,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("同步", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Feedback messaging dialog panel
        if (feedbackMsg.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOperating) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = accentColor, strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = feedbackMsg,
                            fontSize = 11.sp,
                            color = if (feedbackMsg.contains("成功") || feedbackMsg.contains("版本")) accentColor else Color.White
                        )
                    }
                }
            }
        }
    }
}

// ---------------- ANALOG ROTATING VINYL PLATE CD OVERLAY ----------------
@Composable
fun RotatingVinylPlate(
    coverColorStart: Color,
    coverColorEnd: Color,
    isPlayState: Boolean
) {
    var angle by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlayState) {
        if (isPlayState) {
            while (true) {
                angle = (angle + 1f) % 360f
                delay(20)
            }
        }
    }

    val activeAngle by animateFloatAsState(
        targetValue = angle,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "angleAnim"
    )

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlayState) 22f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tonearm"
    )

    Box(
        modifier = Modifier
            .size(300.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Black CD Plate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(activeAngle)
                .clip(CircleShape)
                .background(Color(0xFF0F0F13))
                .border(6.dp, Color(0xFF1B1B22), CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radiusMax = size.width / 2
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = radiusMax * 0.85f,
                    style = Stroke(width = 0.82f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = radiusMax * 0.7f,
                    style = Stroke(width = 0.82f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = radiusMax * 0.55f,
                    style = Stroke(width = 0.82f)
                )
            }

            // Radial colorful gradient visual seed cover in the center
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(coverColorStart, coverColorEnd)
                        )
                    )
                    .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                )
            }
        }

        // Silver needle tonearm overlaying the disk
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-20).dp)
                .graphicsLayer {
                    rotationZ = tonearmAngle
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.8f, 0.2f)
                }
        ) {
            val pivotX = size.width * 0.8f
            val pivotY = size.height * 0.2f

            drawCircle(color = Color(0xFFE2E8F0), radius = 8.dp.toPx(), center = Offset(pivotX, pivotY))
            drawCircle(color = Color(0xFF475569), radius = 4.dp.toPx(), center = Offset(pivotX, pivotY))

            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(pivotX, pivotY),
                end = Offset(size.width * 0.25f, size.height * 0.85f),
                strokeWidth = 3.dp.toPx()
            )

            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(size.width * 0.25f, size.height * 0.85f),
                end = Offset(size.width * 0.12f, size.height * 0.95f),
                strokeWidth = 4.dp.toPx()
            )

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
fun PulseAudioVisualizerBars(bars: List<Float>, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { heightRatio ->
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
                            listOf(accentColor, accentColor.copy(alpha = 0.35f))
                        )
                    )
            )
        }
    }
}

// ---------------- SCROLLING LYRICS LIST VIEWS ----------------
@Composable
fun PlayerLyricsView(
    viewModel: MusicViewModel,
    currentPosition: Long,
    accentColor: Color
) {
    val lyricsLines by viewModel.parsedLyrics.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentLyricsIndex.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyricsLines.size) {
            lazyListState.animateScrollToItem(index = currentIndex, scrollOffset = -180)
        }
    }

    if (lyricsLines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "纯律动音频、未载入匹配的歌词文件",
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
                                .background(accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = line.text,
                        fontSize = fontSizeAnimation.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isActive) accentColor else Color.White,
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

// Mini animated spectrum bars badge
@Composable
fun MiniEqIcon(accentColor: Color) {
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
                    .background(accentColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ---------------- GENERAL MATH & DATES STRING FORMATTERS ----------------
fun formatTimeline(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

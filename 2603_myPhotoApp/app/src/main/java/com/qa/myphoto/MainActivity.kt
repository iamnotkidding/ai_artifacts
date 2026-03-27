package com.qa.myphoto


import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class GalleryMedia(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val isOnline: Boolean,
    val ratio: Float,
    val resolutionText: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [추가] Intent로부터 초기 설정값 읽기
        val initialTabName = intent.getStringExtra("tab_name") ?: "전체"
        val initialAutoScroll = intent.getBooleanExtra("auto_scroll", false)

        setContent {
            MaterialTheme {
                PermissionCheck {
                    Surface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        MainGalleryApp(initialTabName, initialAutoScroll)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCheck(content: @Composable () -> Unit) {
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it.values.all { g -> g }
    }
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
    }
    if (granted) content() else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainGalleryApp(initialTab: String, initialAutoScroll: Boolean) {
    val tabs = listOf("전체", "사진", "동영상", "단말", "온라인")
    
    // [반영] Intent에서 받은 탭 이름에 맞는 인덱스 계산
    val initialPageIndex = remember { 
        val index = tabs.indexOf(initialTab)
        if (index != -1) index else 0
    }
    
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { tabs.size }
    )
    val scope = rememberCoroutineScope()
    
    // [반영] Intent에서 받은 자동 스크롤 여부 설정
    var isAutoScrollEnabled by remember { mutableStateOf(initialAutoScroll) }

    val totalMedia = remember {
        List(100) { i -> 
            val r = if(i % 3 == 0) 0.8f else 1.3f
            GalleryMedia(
                id = "m_$i", 
                uri = Uri.parse("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"), 
                isVideo = i % 5 == 0, 
                isOnline = i % 2 == 0, 
                ratio = r,
                resolutionText = if(r < 1f) "1080:1920" else "1920:1080"
            )
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 16.dp) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = pagerState.currentPage == i, 
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } }
                        ) {
                            Text(title, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                Button(
                    onClick = { isAutoScrollEnabled = !isAutoScrollEnabled },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(if (isAutoScrollEnabled) "자동 스크롤 중지" else "자동 스크롤 시작")
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState, 
            modifier = Modifier.padding(padding).fillMaxSize(),
            userScrollEnabled = true 
        ) { pageIdx ->
            val filtered = when (pageIdx) {
                1 -> totalMedia.filter { !it.isVideo }
                2 -> totalMedia.filter { it.isVideo }
                3 -> totalMedia.filter { !it.isOnline }
                4 -> totalMedia.filter { it.isOnline }
                else -> totalMedia
            }
            PersistentAutoLoopGrid(filtered, isAutoScrollEnabled)
        }
    }
}

@Composable
fun PersistentAutoLoopGrid(items: List<GalleryMedia>, isEnabled: Boolean) {
    val gridState = rememberLazyStaggeredGridState()
    var columnCount by remember { mutableIntStateOf(3) }
    var scrollDirection by remember { mutableIntStateOf(1) } 
    var manualPlayId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isEnabled, scrollDirection) {
        if (isEnabled) {
            while (true) {
                if (scrollDirection == 1 && !gridState.canScrollForward) scrollDirection = -1
                else if (scrollDirection == -1 && !gridState.canScrollBackward) scrollDirection = 1
                gridState.scrollBy(2.5f * scrollDirection)
                delay(16)
            }
        }
    }

    // 화면 전체가 보이는 동영상 자동 재생 로직
    val activeVideoId by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset

            visibleItems.firstOrNull { info ->
                val itemStart = info.offset.y
                val itemEnd = info.offset.y + info.size.height
                val isFullyVisible = itemStart >= viewportStart && itemEnd <= viewportEnd
                val isVideo = items.getOrNull(info.index)?.isVideo == true
                isFullyVisible && isVideo
            }?.key.toString()
        }
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.2f && columnCount > 1) columnCount--
                    else if (zoom < 0.8f && columnCount < 5) columnCount++
                }
            },
        contentPadding = PaddingValues(2.dp),
        verticalItemSpacing = 2.dp,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val isPlaying = item.id == manualPlayId || (manualPlayId == null && item.id == activeVideoId)
            ComfortableMediaCard(
                item = item, 
                isPlaying = isPlaying,
                onPlayClick = { manualPlayId = if (manualPlayId == item.id) null else item.id }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ComfortableMediaCard(item: GalleryMedia, isPlaying: Boolean, onPlayClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Box(modifier = Modifier.aspectRatio(item.ratio), contentAlignment = Alignment.Center) {
            if (item.isVideo && isPlaying) {
                VideoPlayerCore(item.uri)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.uri)
                        .decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Default.VideoCameraBack,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                    )
                    IconButton(onClick = onPlayClick) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            ) {
                Text(text = item.resolutionText, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCore(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { 
            player = exoPlayer; useController = false; 
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) 
        } },
        modifier = Modifier.fillMaxSize()
    )
}

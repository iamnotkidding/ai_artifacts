package com.qa.myphoto


import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.*

data class GalleryMedia(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val ratio: Float,
    val resolutionText: String,
    val isWide: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                PermissionCheck {
                    // [해결 1] 상태바(알림창) 영역 침범 방지
                    Surface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        MainGalleryApp()
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
fun MainGalleryApp() {
    val context = LocalContext.current
    val tabs = listOf("전체", "사진", "동영상")
    val videoImageLoader = remember { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    
    var columnCount by remember { mutableIntStateOf(3) }
    var isAutoScrollEnabled by remember { mutableStateOf(false) }
    val totalMedia = remember { mutableStateListOf<GalleryMedia>() }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val localItems = mutableListOf<GalleryMedia>()
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT)
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val w = cursor.getInt(wCol).coerceAtLeast(1)
                    val h = cursor.getInt(hCol).coerceAtLeast(1)
                    val ratio = w.toFloat() / h
                    val isVideo = (cursor.getString(mimeCol) ?: "").startsWith("video")
                    val uri = ContentUris.withAppendedId(if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    localItems.add(GalleryMedia("local_$id", uri, isVideo, ratio, "${w}x${h}", ratio > 1.3f))
                }
            }
            withContext(Dispatchers.Main) { totalMedia.addAll(localItems) }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }) {
                            Text(title, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (columnCount < 5) columnCount++ }) { Icon(Icons.Default.Remove, "열 늘리기") }
                    Text("${columnCount}단", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (columnCount > 1) columnCount-- }) { Icon(Icons.Default.Add, "열 줄이기") }
                    
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { isAutoScrollEnabled = !isAutoScrollEnabled }) {
                        Text(if (isAutoScrollEnabled) "스크롤 중단" else "자동 스크롤")
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState, 
            modifier = Modifier.padding(padding).fillMaxSize()
        ) { pageIdx ->
            val filtered = when (pageIdx) {
                1 -> totalMedia.filter { !it.isVideo }
                2 -> totalMedia.filter { it.isVideo }
                else -> totalMedia
            }
            
            StrictGaplessGrid(
                items = filtered,
                columns = columnCount,
                isAutoScroll = isAutoScrollEnabled,
                onManualInteraction = { isAutoScrollEnabled = false },
                imageLoader = videoImageLoader
            )
        }
    }
}

@Composable
fun StrictGaplessGrid(items: List<GalleryMedia>, columns: Int, isAutoScroll: Boolean, onManualInteraction: () -> Unit, imageLoader: ImageLoader) {
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    var scrollDirection by remember { mutableIntStateOf(1) }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    
    // [해결 3] 각 아이템의 줌 레벨을 기억하는 상태 맵
    val itemScales = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(isAutoScroll, scrollDirection) {
        if (isAutoScroll) {
            while (isActive) {
                if (scrollDirection == 1 && !gridState.canScrollForward) scrollDirection = -1
                else if (scrollDirection == -1 && !gridState.canScrollBackward) scrollDirection = 1
                gridState.scrollBy(3f * scrollDirection)
                delay(16)
            }
        }
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) onManualInteraction()
    }

    // [해결 4] 화면 중앙에 위치한 영상을 감지하여 자동 재생
    val centerVideoId by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            visibleItems.firstOrNull { info ->
                val isVideo = items.getOrNull(info.index)?.isVideo == true
                isVideo && info.offset.y >= layoutInfo.viewportStartOffset && (info.offset.y + info.size.height) <= layoutInfo.viewportEndOffset
            }?.key.toString()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            verticalItemSpacing = 0.dp,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(items, key = { it.id }, span = { item ->
                val scale = itemScales[item.id] ?: 1f
                // [해결 3] 줌 레벨이 1.2를 넘어가면 가로 전체 폭(FullLine)을 차지하도록 재배치하여 겹침 방지
                if (scale > 1.2f || item.isWide || columns == 1) {
                    StaggeredGridItemSpan.FullLine 
                } else {
                    StaggeredGridItemSpan.SingleLane
                }
            }) { item ->
                val isPlaying = item.id == activeVideoId || (activeVideoId == null && item.id == centerVideoId)
                val currentScale = itemScales[item.id] ?: 1f
                
                InteractiveMediaCard(
                    item = item,
                    isPlaying = isPlaying,
                    scale = currentScale,
                    onScaleChange = { newScale -> itemScales[item.id] = newScale },
                    imageLoader = imageLoader,
                    onPlayToggle = { activeVideoId = if (activeVideoId == item.id) null else item.id }
                )
            }
        }

        // 최상단/최하단 퀵 이동 버튼
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = gridState.firstVisibleItemIndex > 0) {
                FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }, modifier = Modifier.size(48.dp)) { 
                    Icon(Icons.Default.VerticalAlignTop, "맨 위로") 
                }
            }
            AnimatedVisibility(visible = gridState.canScrollForward) {
                FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(items.size - 1) } }, modifier = Modifier.size(48.dp)) { 
                    Icon(Icons.Default.VerticalAlignBottom, "맨 아래로") 
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun InteractiveMediaCard(
    item: GalleryMedia, 
    isPlaying: Boolean, 
    scale: Float, 
    onScaleChange: (Float) -> Unit, 
    imageLoader: ImageLoader, 
    onPlayToggle: () -> Unit
) {
    val context = LocalContext.current
    var offset by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // [해결 3] scale이 커질수록 aspectRatio가 줄어들어 세로 길이가 길어짐 -> 겹치지 않고 레이아웃을 밀어냄
            .aspectRatio((item.ratio / scale).coerceAtLeast(0.2f))
            .pointerInput(Unit) {
                // [해결 2] 1손가락 스크롤과 2손가락 줌 분리
                detectTwoFingerGesture { pan, zoom ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    onScaleChange(newScale)
                    if (newScale > 1f) offset += pan else offset = Offset.Zero
                }
            },
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val modifierWithPan = Modifier
                .fillMaxSize()
                .graphicsLayer(translationX = offset.x, translationY = offset.y)

            // 깜빡임 방지용 배경 썸네일
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).memoryCachePolicy(CachePolicy.ENABLED).crossfade(200).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop, 
                modifier = modifierWithPan
            )
            
            if (item.isVideo) {
                if (isPlaying) {
                    VideoPlayerCore(item.uri, modifierWithPan)
                } else {
                    Icon(Icons.Default.VideoCameraBack, null, tint = Color.White.copy(0.7f), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp))
                    IconButton(onClick = onPlayToggle) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                }
            }
            
            if (scale == 1f) {
                Surface(color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                    Text(text = item.resolutionText, color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

// [해결 2] 2개 이상의 손가락이 닿았을 때만 이벤트를 가로채는 커스텀 제스처 디텍터
suspend fun PointerInputScope.detectTwoFingerGesture(onGesture: (pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pointers = event.changes.filter { it.pressed }
            if (pointers.size >= 2) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                onGesture(pan, zoom)
                // 2손가락 이상일 때만 이벤트를 소비하여 1손가락 스크롤은 방해받지 않음
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (pointers.isNotEmpty())
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCore(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { exoPlayer.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier
    )
}

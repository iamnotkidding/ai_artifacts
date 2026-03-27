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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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
    var ratio: Float,
    val resolutionText: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                PermissionCheck {
                    MainGalleryApp()
                }
            }
        }
    }
}

@Composable
fun PermissionCheck(content: @Composable () -> Unit) {
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it.values.all { g -> g } }
    LaunchedEffect(Unit) { launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) }
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
    
    // 1. [복구] 컨트롤 버튼용 레이아웃 레벨 (열 개수)
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
                    val isVideo = (cursor.getString(mimeCol) ?: "").startsWith("video")
                    localItems.add(GalleryMedia("local_$id", ContentUris.withAppendedId(if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), isVideo, w.toFloat() / h, "${w}x${h}"))
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
                // [복구] 상단 컨트롤 인터페이스
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (columnCount < 5) columnCount++ }) { Icon(Icons.Default.Remove, "축소") }
                    Text("${columnCount}열", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (columnCount > 1) columnCount-- }) { Icon(Icons.Default.Add, "확대") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { isAutoScrollEnabled = !isAutoScrollEnabled }) {
                        Text(if (isAutoScrollEnabled) "중단" else "자동 스크롤")
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { pageIdx ->
            val filtered = when (pageIdx) { 1 -> totalMedia.filter { !it.isVideo }; 2 -> totalMedia.filter { it.isVideo }; else -> totalMedia }
            DynamicResizingGrid(filtered, columnCount, isAutoScrollEnabled, videoImageLoader) { isAutoScrollEnabled = false }
        }
    }
}

@Composable
fun DynamicResizingGrid(items: List<GalleryMedia>, columns: Int, isAutoScroll: Boolean, imageLoader: ImageLoader, onInteraction: () -> Unit) {
    val gridState = rememberLazyStaggeredGridState()
    var scrollDir by remember { mutableIntStateOf(1) }
    var activeId by remember { mutableStateOf<String?>(null) }

    // 자동 스크롤 동기화
    LaunchedEffect(isAutoScroll, scrollDir) {
        if (isAutoScroll) {
            while (isActive) {
                if (scrollDir == 1 && !gridState.canScrollForward) scrollDir = -1
                else if (scrollDir == -1 && !gridState.canScrollBackward) scrollDir = 1
                gridState.scrollBy(2.5f * scrollDir)
                delay(16)
            }
        }
    }
    LaunchedEffect(gridState.isScrollInProgress) { if (gridState.isScrollInProgress) onInteraction() }

    // 비디오 자동 재생 감지
    val topVideoId by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                items.getOrNull(info.index)?.isVideo == true && info.offset.y >= 0
            }?.key.toString()
        }
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(1.dp),
        verticalItemSpacing = 1.dp,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(items, key = { it.id }, span = { item ->
            // [반영] 특정 아이템이 선택(Play)되거나 비율이 매우 크면 한 줄을 다 차지하도록 유동적 재배치
            if (activeId == item.id && columns > 1) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
        }) { item ->
            val isPlaying = item.id == activeId || (activeId == null && item.id == topVideoId)
            
            // [반영] 2, 3, 4번 요구사항 구현: 줌에 따른 확대 및 재배치 카드
            ZoomReLayoutCard(item, isPlaying, imageLoader) {
                activeId = if (activeId == item.id) null else item.id
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ZoomReLayoutCard(item: GalleryMedia, isPlaying: Boolean, imageLoader: ImageLoader, onToggle: () -> Unit) {
    val context = LocalContext.current
    // 개별 줌 상태 (재배치를 위해 활용 가능)
    var localScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .pointerInput(Unit) {
                // [반영] 두 손가락 줌/이동 제스처
                detectTransformGestures { _, pan, zoom, _ ->
                    localScale = (localScale * zoom).coerceIn(1f, 4f)
                    if (localScale > 1f) offset += pan
                    else offset = androidx.compose.ui.geometry.Offset.Zero
                }
            },
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(item.ratio) // 비율 유지로 겹침 방지
                .graphicsLayer(
                    scaleX = localScale,
                    scaleY = localScale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.isVideo) {
                if (isPlaying) VideoPlayer(item.uri)
                else IconButton(onClick = onToggle, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Text(item.resolutionText, color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(0.5f)))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(uri: Uri) {
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
    AndroidView(factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }, modifier = Modifier.fillMaxSize())
}

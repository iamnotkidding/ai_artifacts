요청하신 대로 레이아웃의 가로/세로 절대 최소 크기를 기존 64dp에서 정확히 절반인 32dp로 축소했습니다.
이제 화면을 축소(핀치 줌)할 때 파일들이 훨씬 더 작고 촘촘하게 배열될 수 있으며, 최소 크기에 도달했을 때 동영상 재생 버튼 등의 UI가 깔끔하게 숨겨지는 스마트 로직은 그대로 유지됩니다.
아래 최종 코드로 전체를 덮어씌워 주세요!
💻 MainActivity.kt 최종 통합 소스 코드
package com.example.photogallery

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.C
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
import kotlin.math.abs

data class GalleryMedia(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val resolutionText: String,
    val ratio: Float,
    val isWide: Boolean
)

data class RowData(
    val items: List<GalleryMedia>,
    val rowHeightDp: Float,
    val itemWidthsDp: Map<String, Float>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                PermissionCheckAPI34 {
                    Surface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        MainGalleryApp()
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCheckAPI34(content: @Composable () -> Unit) {
    var isGranted by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val imagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        val videoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: false
        val visualUserSelectedGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] ?: false
        } else false
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

        isGranted = (imagesGranted && videoGranted) || visualUserSelectedGranted || storageGranted
    }
    
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionsToRequest.apply {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.apply {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        launcher.launch(permissionsToRequest.toTypedArray())
    }
    
    if (isGranted) content() else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainGalleryApp() {
    val context = LocalContext.current
    val tabs = listOf("전체", "사진", "동영상", "커스텀")
    
    val videoImageLoader = remember { 
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build() 
    }
    
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    
    var maxItemsPerRow by remember { mutableIntStateOf(3) }
    var isAutoScrollEnabled by remember { mutableStateOf(false) }
    val totalMedia = remember { mutableStateListOf<GalleryMedia>() }
    
    val itemScales = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val localItems = mutableListOf<GalleryMedia>()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID, 
                MediaStore.MediaColumns.MIME_TYPE, 
                MediaStore.MediaColumns.WIDTH, 
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATE_ADDED
            )
            
            try {
                context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val wCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                    val hCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val w = maxOf(1, cursor.getInt(wCol))
                        val h = maxOf(1, cursor.getInt(hCol))
                        val ratio = w.toFloat() / h
                        val isVideo = (cursor.getString(mimeCol) ?: "").startsWith("video")
                        val uri = ContentUris.withAppendedId(if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        
                        localItems.add(GalleryMedia("local_$id", uri, isVideo, "${w}x${h}", ratio, ratio > 1.3f))
                    }
                }
                withContext(Dispatchers.Main) { totalMedia.addAll(localItems) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { itemScales.clear() }) { 
                        Icon(Icons.Default.Refresh, "재배치 초기화") 
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(onClick = { if (maxItemsPerRow < 10) maxItemsPerRow++ }) { Icon(Icons.Default.Remove, "작게") }
                    Text("최대 ${maxItemsPerRow}개 배치", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (maxItemsPerRow > 1) maxItemsPerRow-- }) { Icon(Icons.Default.Add, "크게") }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(onClick = { isAutoScrollEnabled = !isAutoScrollEnabled }) {
                        Text(if (isAutoScrollEnabled) "정지" else "자동")
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState, 
            modifier = Modifier.padding(padding).fillMaxSize()
        ) { pageIdx ->
            val filtered = remember(pageIdx, totalMedia.size) {
                when (pageIdx) {
                    1 -> totalMedia.filter { !it.isVideo }
                    2 -> totalMedia.filter { it.isVideo }
                    3 -> totalMedia.filter { it.isVideo }
                    else -> totalMedia.toList() 
                }
            }
            
            OptimalFluidGallery(
                items = filtered,
                maxItemsPerRow = maxItemsPerRow,
                itemScales = itemScales, 
                isAutoScroll = isAutoScrollEnabled,
                onManualInteraction = { isAutoScrollEnabled = false },
                imageLoader = videoImageLoader,
                isCustomTab = pageIdx == 3
            )
        }
    }
}

@Composable
fun OptimalFluidGallery(
    items: List<GalleryMedia>, 
    maxItemsPerRow: Int, 
    itemScales: MutableMap<String, Float>, 
    isAutoScroll: Boolean, 
    onManualInteraction: () -> Unit, 
    imageLoader: ImageLoader,
    isCustomTab: Boolean
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollDirection by remember { mutableIntStateOf(1) }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val density = LocalDensity.current.density

    // [요구사항 반영] 가로/세로 절대 최소 크기를 기존 64dp에서 절반인 32dp로 축소
    val MIN_DIMENSION_DP = 32f

    val rows = remember(items, maxItemsPerRow, itemScales.toMap(), screenWidthDp) {
        val rowDataList = mutableListOf<RowData>()
        if (items.isEmpty()) return@remember rowDataList

        val targetWeightSum = maxItemsPerRow.toFloat()
        val minLogicalWeightBase = (MIN_DIMENSION_DP / screenWidthDp) * targetWeightSum

        var i = 0
        while (i < items.size) {
            val currentRowItems = mutableListOf<GalleryMedia>()
            var currentRowWeightSum = 0f

            var j = i
            while (j < items.size) {
                val item = items[j]
                val scale = (itemScales[item.id] ?: 1f).coerceAtLeast(0.1f)
                val constrainedRatio = item.ratio.coerceIn(0.5f, 2.5f)
                
                val requiredMinWeight = minLogicalWeightBase * maxOf(1f, constrainedRatio)
                val originalWeight = constrainedRatio * scale
                val effectiveWeight = maxOf(originalWeight, requiredMinWeight)

                val testRow = currentRowItems + item
                val testWeightSum = currentRowWeightSum + effectiveWeight
                val gapCount = testRow.size - 1
                val availableWidth = screenWidthDp - (gapCount * 2f)

                var violateMinSize = false
                for (testItem in testRow) {
                    val tScale = (itemScales[testItem.id] ?: 1f).coerceAtLeast(0.1f)
                    val tConstrainedRatio = testItem.ratio.coerceIn(0.5f, 2.5f)
                    val tRequiredMinWeight = minLogicalWeightBase * maxOf(1f, tConstrainedRatio)
                    val tWeight = maxOf(tConstrainedRatio * tScale, tRequiredMinWeight)
                    
                    val projectedWidth = availableWidth * (tWeight / testWeightSum)
                    val requiredMinWidthDp = MIN_DIMENSION_DP * maxOf(1f, tConstrainedRatio)
                    
                    if (projectedWidth < requiredMinWidthDp - 0.1f) {
                        violateMinSize = true
                        break
                    }
                }

                val overTarget = testWeightSum > targetWeightSum * 1.5f

                if (currentRowItems.isEmpty()) {
                    currentRowItems.add(item)
                    currentRowWeightSum += effectiveWeight
                    j++
                } else if (violateMinSize || overTarget) {
                    break
                } else {
                    currentRowItems.add(item)
                    currentRowWeightSum += effectiveWeight
                    j++
                }
            }

            val gapCount = currentRowItems.size - 1
            val availableWidth = screenWidthDp - (gapCount * 2f)
            
            val itemWidths = mutableMapOf<String, Float>()
            var maxRowHeightDp = 0f

            val isLastRow = j == items.size
            val effectiveTotalWeight = if (isLastRow && currentRowWeightSum < targetWeightSum * 0.8f) {
                targetWeightSum 
            } else {
                currentRowWeightSum
            }

            for (item in currentRowItems) {
                val scale = (itemScales[item.id] ?: 1f).coerceAtLeast(0.1f)
                val constrainedRatio = item.ratio.coerceIn(0.5f, 2.5f)
                val requiredMinWeight = minLogicalWeightBase * maxOf(1f, constrainedRatio)
                val weight = maxOf(constrainedRatio * scale, requiredMinWeight)
                
                val widthDp = availableWidth * (weight / effectiveTotalWeight)
                itemWidths[item.id] = widthDp

                val heightDp = widthDp / item.ratio
                if (heightDp > maxRowHeightDp) {
                    maxRowHeightDp = heightDp
                }
            }

            rowDataList.add(RowData(currentRowItems, maxRowHeightDp, itemWidths))
            i = j
        }
        rowDataList
    }

    LaunchedEffect(isAutoScroll, scrollDirection) {
        if (isAutoScroll) {
            while (isActive) {
                if (scrollDirection == 1 && !listState.canScrollForward) scrollDirection = -1
                else if (scrollDirection == -1 && !listState.canScrollBackward) scrollDirection = 1
                listState.scrollBy(3f * scrollDirection)
                delay(16)
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onManualInteraction()
    }

    val centerVideoId by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null

            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            var closestId: String? = null
            var minDistance = Float.MAX_VALUE

            for (rowInfo in visibleItems) {
                val rowCenter = rowInfo.offset + (rowInfo.size / 2)
                val distance = abs(viewportCenter - rowCenter).toFloat()
                
                val rowData = rows.getOrNull(rowInfo.index) ?: continue
                for (item in rowData.items) {
                    if (item.isVideo && distance < minDistance) {
                        minDistance = distance
                        closestId = item.id
                    }
                }
            }
            closestId
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(rows) { _, rowData ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowData.rowHeightDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rowData.items.forEach { item ->
                        val isPlaying = item.id == activeVideoId || (activeVideoId == null && item.id == centerVideoId)
                        val computedWidthDp = rowData.itemWidthsDp[item.id] ?: 100f
                        
                        val constrainedRatio = item.ratio.coerceIn(0.5f, 2.5f)
                        val requiredMinWidthDp = MIN_DIMENSION_DP * maxOf(1f, constrainedRatio)
                        val isAtMinSize = computedWidthDp <= requiredMinWidthDp + 2f 

                        Box(modifier = Modifier.width(computedWidthDp.dp).fillMaxHeight()) {
                            DynamicRatioMediaCard(
                                item = item,
                                isPlaying = isPlaying,
                                isAtMinSize = isAtMinSize,
                                onScaleChange = { zoomDelta ->
                                    val currentScale = itemScales[item.id] ?: 1f
                                    val newScale = (currentScale * zoomDelta).coerceIn(0.1f, 10f)
                                    itemScales[item.id] = newScale
                                },
                                imageLoader = imageLoader,
                                onPlayToggle = { activeVideoId = if (activeVideoId == item.id) null else item.id }
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = listState.firstVisibleItemIndex > 0) {
                FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.size(48.dp)) { 
                    Icon(Icons.Default.VerticalAlignTop, "위로") 
                }
            }
            AnimatedVisibility(visible = listState.canScrollForward) {
                FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(rows.size - 1) } }, modifier = Modifier.size(48.dp)) { 
                    Icon(Icons.Default.VerticalAlignBottom, "아래로") 
                }
            }
        }

        if (isCustomTab && activeVideoId != null) {
            val scale = (itemScales[activeVideoId!!] ?: 1f).coerceAtLeast(0.1f)
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "선택된 동영상 크기 조절: ${String.format("%.1f", scale)}x", 
                    color = Color.White, 
                    fontSize = 14.sp
                )
                Slider(
                    value = scale,
                    onValueChange = { newScale -> 
                        itemScales[activeVideoId!!] = newScale 
                        
                        val rIndex = rows.indexOfFirst { row -> row.items.any { it.id == activeVideoId } }
                        if (rIndex >= 0) {
                            val rowHeightPx = (rows[rIndex].rowHeightDp * density).toInt()
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            val offset = (viewportHeight / 2) - (rowHeightPx / 2)
                            
                            scope.launch {
                                listState.scrollToItem(rIndex, offset)
                            }
                        }
                    },
                    valueRange = 0.1f..4f, 
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DynamicRatioMediaCard(
    item: GalleryMedia, 
    isPlaying: Boolean, 
    isAtMinSize: Boolean,
    onScaleChange: (Float) -> Unit, 
    imageLoader: ImageLoader, 
    onPlayToggle: () -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }

    var isVideoReady by remember { mutableStateOf(false) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady && isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "videoFadeOutIn"
    )

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isVideoReady = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize() 
            .pointerInput(Unit) {
                detectTwoFingerGesture(
                    onGesture = { zoom ->
                        onScaleChange(zoom)
                    }
                )
            },
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.White) 
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).memoryCachePolicy(CachePolicy.ENABLED).crossfade(200).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop, 
                modifier = Modifier.fillMaxSize()
            )
            
            if (item.isVideo && isPlaying) {
                Box(modifier = Modifier.fillMaxSize().alpha(videoAlpha)) {
                    VideoPlayerCore(
                        uri = item.uri, 
                        isMuted = isMuted,
                        onFirstFrameRendered = { isVideoReady = true }
                    )
                }
            }
            
            if (item.isVideo) {
                if (!isPlaying && !isAtMinSize) {
                    IconButton(onClick = onPlayToggle) { 
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
                    }
                }
                
                if (!isAtMinSize) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, 
                            contentDescription = if (isMuted) "음소거 해제" else "음소거", 
                            tint = Color.White.copy(0.9f), 
                            modifier = Modifier.size(18.dp) 
                        )
                    }
                }
            }
            
            if (!isAtMinSize) {
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                ) {
                    Text(
                        text = item.resolutionText, 
                        color = Color.White, 
                        fontSize = 10.sp, 
                        style = TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(1f, 1f),
                                blurRadius = 4f
                            )
                        )
                    )
                }
            }
        }
    }
}

suspend fun PointerInputScope.detectTwoFingerGesture(
    onGesture: (zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pointers = event.changes.filter { it.pressed }

            if (pointers.size >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onGesture(zoom)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else if (pointers.isEmpty()) {
                break
            }
        } while (true)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCore(uri: Uri, isMuted: Boolean, onFirstFrameRendered: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (isMuted) 0f else 1f
            
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, !isMuted)
            
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, isMuted)
                .build()
                
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    onFirstFrameRendered()
                }
            })
                
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, isMuted)
            .build()
        exoPlayer.setAudioAttributes(exoPlayer.audioAttributes, !isMuted)
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
        modifier = Modifier.fillMaxSize()
    )
}


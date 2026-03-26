package com.qa.myphoto

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- [데이터 모델] ---
data class GalleryItem(
    val id: Int,
    val url: String,
    val isVideo: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GooglePhotosCloneApp()
                }
            }
        }
    }
}

@Composable
fun GooglePhotosCloneApp() {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()

    // 1 & 5. [줌 인/아웃] 및 [Comfortable 모드] 열 개수 상태
    var columnCount by remember { mutableIntStateOf(3) }
    val animatedColumns by animateIntAsState(targetValue = columnCount, label = "GridZoom")

    // 3. [필터] 상태 (전체, 사진, 동영상)
    var currentFilter by remember { mutableStateOf("ALL") }

    // 4. [자동 스크롤] 상태
    var isAutoScrollRunning by remember { mutableStateOf(false) }

    // 샘플 데이터 생성 (실제 앱에서는 MediaStore에서 로드)
    val allItems = remember {
        List(100) { i ->
            GalleryItem(
                id = i,
                url = "https://picsum.photos/id/${i + 20}/400/${if (i % 3 == 0) 600 else 400}",
                isVideo = i % 5 == 0 // 5번째마다 비디오로 설정
            )
        }
    }

    // 필터링된 리스트
    val filteredList = remember(currentFilter) {
        when (currentFilter) {
            "PHOTOS" -> allItems.filter { !it.isVideo }
            "VIDEOS" -> allItems.filter { it.isVideo }
            else -> allItems
        }
    }

    // 4. [자동 스크롤] 로직 구현
    LaunchedEffect(isAutoScrollRunning) {
        if (isAutoScrollRunning) {
            while (true) {
                gridState.scrollBy(2f)
                delay(16) // 약 60fps
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 필터 및 컨트롤 바
        FilterControlBar(
            currentFilter = currentFilter,
            onFilterChange = { currentFilter = it },
            isAutoScroll = isAutoScrollRunning,
            onAutoScrollToggle = { isAutoScrollRunning = !isAutoScrollRunning }
        )

        // 메인 그리드 영역
        Box(modifier = Modifier
            .fillMaxSize()
            // 5. [줌 인/아웃] 제스처 감지
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.2f && columnCount > 1) columnCount--
                    else if (zoom < 0.8f && columnCount < 6) columnCount++
                }
            }
        ) {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(animatedColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                verticalItemSpacing = 2.dp,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    // 2. [영상 미리보기 자동 재생] 로직이 포함된 아이템 뷰
                    MediaItemCard(item = item, gridState = gridState)
                }
            }
        }
    }
}

@Composable
fun FilterControlBar(
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    isAutoScroll: Boolean,
    onAutoScrollToggle: () -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = when(currentFilter) { "ALL" -> 0; "PHOTOS" -> 1; else -> 2 },
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Tab(selected = currentFilter == "ALL", onClick = { onFilterChange("ALL") }) {
            Text("전체", modifier = Modifier.padding(12.dp))
        }
        Tab(selected = currentFilter == "PHOTOS", onClick = { onFilterChange("PHOTOS") }) {
            Text("사진", modifier = Modifier.padding(12.dp))
        }
        Tab(selected = currentFilter == "VIDEOS", onClick = { onFilterChange("VIDEOS") }) {
            Text("동영상", modifier = Modifier.padding(12.dp))
        }
        Tab(selected = isAutoScroll, onClick = onAutoScrollToggle) {
            Text(if (isAutoScroll) "스크롤 정지" else "자동 스크롤", color = Color.Red, modifier = Modifier.padding(12.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MediaItemCard(item: GalleryItem, gridState: LazyStaggeredGridState) {
    val context = LocalContext.current
    // 현재 아이템이 화면에 보이는지 여부 확인 (간단한 로직)
    val isVisible = remember {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            visibleItems.any { it.key == item.id }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.isVideo && isVisible.value) {
                // 2. [영상 자동 재생] 구현 (ExoPlayer)
                VideoPreviewPlayer(url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            } else {
                // 일반 사진 표시 (Coil)
                AsyncImage(
                    model = item.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
                if (item.isVideo) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPlayer(url: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = 0f // 미리보기이므로 음소거
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // 컨트롤러 숨김 (미리보기 모드)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxWidth().height(200.dp)
    )
}
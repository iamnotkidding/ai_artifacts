package com.mediatest.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var recycler: RecyclerView
    private lateinit var overlay: TestOverlayView
    private lateinit var btnAutoScroll: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnReport: ImageButton

    // ── State ─────────────────────────────────────────────────────────────
    private lateinit var adapter: VideoFeedAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private val session = TestSession()
    private lateinit var config: AutomationConfig

    var isAutoScrollEnabled = true
    var isAutoRotateEnabled = true
    var isAutoPipEnabled    = true
    var isAutoZoomEnabled   = true
    var isPipActive         = false
        private set

    private var isLandscape = false

    // ── Coroutine jobs ────────────────────────────────────────────────────
    private var scrollJob:  Job? = null
    private var rotateJob:  Job? = null
    private var pipJob:     Job? = null
    private var zoomJob:    Job? = null

    // ─────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        config = AutomationConfig.load(this)

        bindViews()
        setupRecycler()
        setupButtons()
        startAllAutomation()
    }

    // ── Binding ───────────────────────────────────────────────────────────

    private fun bindViews() {
        recycler       = findViewById(R.id.videoFeedRecycler)
        overlay        = findViewById(R.id.testOverlayView)
        btnAutoScroll  = findViewById(R.id.btnAutoScroll)
        btnRotate      = findViewById(R.id.btnRotate)
        btnPip         = findViewById(R.id.btnPip)
        btnSettings    = findViewById(R.id.btnSettings)
        btnReport      = findViewById(R.id.btnReport)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────

    private fun setupRecycler() {
        layoutManager = LinearLayoutManager(this)
        recycler.layoutManager = layoutManager

        adapter = VideoFeedAdapter(this, VideoSources.items, session)
        recycler.adapter = adapter

        PagerSnapHelper().attachToRecyclerView(recycler)

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos  = layoutManager.findFirstVisibleItemPosition()
                    val item = VideoSources.items[pos % VideoSources.items.size]
                    session.recordVideoSwitch(item)
                    updateOverlay()
                }
            }
        })
    }

    // ── Buttons ───────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnAutoScroll.setOnClickListener {
            isAutoScrollEnabled = !isAutoScrollEnabled
            toast("Auto Scroll: ${if (isAutoScrollEnabled) "ON" else "OFF"}")
            updateOverlay()
        }
        btnRotate.setOnClickListener {
            isAutoRotateEnabled = !isAutoRotateEnabled
            toast("Auto Rotate: ${if (isAutoRotateEnabled) "ON" else "OFF"}")
            updateOverlay()
        }
        btnPip.setOnClickListener { triggerPip() }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnReport.setOnClickListener {
            startActivity(
                Intent(this, TestReportActivity::class.java)
                    .putExtra("session", session)
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTOMATION — coroutine loops (lifecycle-scoped, auto-cancelled)
    // ════════════════════════════════════════════════════════════════════════

    private fun startAllAutomation() {
        startScrollLoop()
        startRotateLoop()
        startPipLoop()
        startZoomLoop()
    }

    private fun startScrollLoop() {
        scrollJob = lifecycleScope.launch {
            delay(config.scrollIntervalMs)
            while (isActive) {
                if (isAutoScrollEnabled && !isPipActive) {
                    scrollToNext()
                }
                delay(config.scrollIntervalMs)
            }
        }
    }

    private fun startRotateLoop() {
        rotateJob = lifecycleScope.launch {
            delay(config.rotateIntervalMs)
            while (isActive) {
                if (isAutoRotateEnabled && !isPipActive) {
                    flipOrientation()
                }
                delay(config.rotateIntervalMs)
            }
        }
    }

    private fun startPipLoop() {
        pipJob = lifecycleScope.launch {
            delay(config.pipIntervalMs)
            while (isActive) {
                if (isAutoPipEnabled) triggerPip()
                delay(config.pipIntervalMs)
            }
        }
    }

    private fun startZoomLoop() {
        val zoomLevels = floatArrayOf(1.0f, 1.5f, 2.0f, 1.5f, 1.0f)
        var idx = 0
        zoomJob = lifecycleScope.launch {
            delay(config.zoomIntervalMs)
            while (isActive) {
                if (isAutoZoomEnabled && !isPipActive) {
                    val scale = zoomLevels[idx % zoomLevels.size]
                    idx++
                    applyZoomToCurrent(scale)
                    session.recordZoom(scale)
                    updateOverlay()
                }
                delay(config.zoomIntervalMs)
            }
        }
    }

    // ── Automation actions ────────────────────────────────────────────────

    private fun scrollToNext() {
        val current = layoutManager.findFirstVisibleItemPosition()
        val next    = (current + 1) % VideoSources.items.size
        recycler.smoothScrollToPosition(next)
        session.recordAutoScroll()
        updateOverlay()
    }

    private fun flipOrientation() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        session.recordRotation(if (isLandscape) "LANDSCAPE" else "PORTRAIT")
        updateOverlay()
    }

    private fun triggerPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isPipActive) enterPipMode()
        // Exit is detected via onPictureInPictureModeChanged
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()
            enterPictureInPictureMode(params)
            session.recordPipToggle(true)
        }
    }

    private fun applyZoomToCurrent(scale: Float) {
        val pos = layoutManager.findFirstVisibleItemPosition()
        val vh  = recycler.findViewHolderForAdapterPosition(pos)
        (vh as? VideoFeedAdapter.VideoViewHolder)?.applyZoom(scale)
    }

    // ── PiP lifecycle ─────────────────────────────────────────────────────

    override fun onPictureInPictureModeChanged(
        inPip: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        isPipActive = inPip
        session.recordPipToggle(inPip)
        updateOverlay()

        if (!inPip) {
            // Back to full-screen: release orientation lock
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    // ── Overlay ───────────────────────────────────────────────────────────

    private fun updateOverlay() {
        overlay.update(session, isAutoScrollEnabled, isAutoRotateEnabled, isAutoPipEnabled, isPipActive)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        adapter.resumeCurrentPlayer()
        updateOverlay()
    }

    override fun onPause() {
        super.onPause()
        if (!isPipActive) adapter.pauseCurrentPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.releaseAll()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

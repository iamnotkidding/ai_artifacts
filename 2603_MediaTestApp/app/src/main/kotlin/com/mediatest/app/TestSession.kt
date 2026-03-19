package com.mediatest.app

import java.io.Serializable

/**
 * Accumulates all test events for the current run.
 * Serializable so it can be passed via Intent extras.
 */
class TestSession : Serializable {

    // ── Counters ──────────────────────────────────────────────────────────
    var totalVideoSwitches = 0
    var totalAutoScrolls   = 0
    var totalRotations     = 0
    var totalPipEnters     = 0
    var totalPipExits      = 0
    var totalZoomEvents    = 0
    var totalLoadEvents    = 0
    var totalReadyEvents   = 0

    // ── Current state ─────────────────────────────────────────────────────
    var currentOrientation = "PORTRAIT"
    var currentZoom        = 1.0f
    var isPip              = false
    var currentVideoTitle  = ""
    var currentFormat      = ""

    // ── Log (newest first, max 200 entries) ───────────────────────────────
    val eventLog = ArrayDeque<String>()

    // ── Format hit map ────────────────────────────────────────────────────
    val formatHits = mutableMapOf<String, Int>()

    // ── Session timing ────────────────────────────────────────────────────
    val startTimeMs = System.currentTimeMillis()

    // ─────────────────────────────────────────────────────────────────────

    fun recordVideoSwitch(item: VideoItem) {
        totalVideoSwitches++
        currentVideoTitle = item.title
        currentFormat     = item.formatDescription
        formatHits[item.format.badgeLabel] = (formatHits[item.format.badgeLabel] ?: 0) + 1
        log("▶ Video: ${item.title} [${item.format.badgeLabel}]")
    }

    fun recordAutoScroll() {
        totalAutoScrolls++
        log("⏩ Auto-scroll #$totalAutoScrolls")
    }

    fun recordRotation(orientation: String) {
        totalRotations++
        currentOrientation = orientation
        log("🔄 Rotate → $orientation (#$totalRotations)")
    }

    fun recordPipToggle(entering: Boolean) {
        isPip = entering
        if (entering) {
            totalPipEnters++
            log("📺 PiP Enter #$totalPipEnters")
        } else {
            totalPipExits++
            log("📺 PiP Exit  #$totalPipExits")
        }
    }

    fun recordZoom(scale: Float) {
        totalZoomEvents++
        currentZoom = scale
        log("🔍 Zoom → ${scale}x (#$totalZoomEvents)")
    }

    fun recordVideoLoad(item: VideoItem) {
        totalLoadEvents++
        log("⏳ Load: ${item.title}")
    }

    fun recordVideoReady(item: VideoItem) {
        totalReadyEvents++
        log("✅ Ready: ${item.title}")
    }

    fun elapsedSeconds() = (System.currentTimeMillis() - startTimeMs) / 1000L

    private fun log(msg: String) {
        eventLog.addFirst("[${elapsedSeconds()}s] $msg")
        if (eventLog.size > 200) eventLog.removeLast()
    }
}

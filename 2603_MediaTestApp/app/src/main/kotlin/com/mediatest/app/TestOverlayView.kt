package com.mediatest.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay drawn on top of the video feed.
 * Shows real-time: orientation, zoom, PiP state, and event counters.
 */
class TestOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // ── Paints ────────────────────────────────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = dp(11)
        typeface  = Typeface.MONOSPACE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val panelRect = RectF()

    // ── State ─────────────────────────────────────────────────────────────
    private var session: TestSession? = null
    private var autoScroll = true
    private var autoRotate = true
    private var autoPip    = true
    private var isPip      = false

    // ── Colours ───────────────────────────────────────────────────────────
    private val colGreen = Color.parseColor("#00E676")
    private val colAmber = Color.parseColor("#FFD740")
    private val colRed   = Color.parseColor("#FF5252")
    private val colBlue  = Color.parseColor("#40C4FF")

    init { setWillNotDraw(false) }

    fun update(
        session: TestSession,
        autoScroll: Boolean,
        autoRotate: Boolean,
        autoPip: Boolean,
        isPip: Boolean,
    ) {
        this.session    = session
        this.autoScroll = autoScroll
        this.autoRotate = autoRotate
        this.autoPip    = autoPip
        this.isPip      = isPip
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = session ?: return

        val pad  = dp(8)
        val lh   = dp(15)
        val panW = dp(210)
        val panH = lh * 12 + pad * 2

        // Panel background
        panelRect.set(pad, pad, pad + panW, pad + panH)
        canvas.drawRoundRect(panelRect, dp(6), dp(6), bgPaint)

        var tx = pad * 2
        var ty = pad * 2 + lh

        // ── Header ───────────────────────────────────────────────────────
        textPaint.apply { color = colBlue; textSize = dp(12); isFakeBoldText = true }
        canvas.drawText("◉  MEDIA TEST HUD", tx, ty, textPaint)
        ty += lh * 1.4f

        textPaint.apply { textSize = dp(10); isFakeBoldText = false; color = Color.WHITE }

        // Elapsed
        canvas.drawText("Elapsed : ${s.elapsedSeconds()} s", tx, ty, textPaint); ty += lh

        // Current video (truncated)
        val title = s.currentVideoTitle.take(22).let { if (it.length < s.currentVideoTitle.length) "$it…" else it }
        canvas.drawText("Video   : $title", tx, ty, textPaint); ty += lh
        canvas.drawText("Format  : ${s.currentFormat}", tx, ty, textPaint); ty += lh

        // Counters
        canvas.drawText("Switches: ${s.totalVideoSwitches}   Scrolls: ${s.totalAutoScrolls}", tx, ty, textPaint); ty += lh
        canvas.drawText("Rotates : ${s.totalRotations}   Zooms  : ${s.totalZoomEvents}", tx, ty, textPaint); ty += lh
        canvas.drawText("PiP ↑   : ${s.totalPipEnters}   PiP ↓  : ${s.totalPipExits}", tx, ty, textPaint); ty += lh

        // State dots row
        ty += dp(2)
        drawStatDot(canvas, tx,          ty, if (autoScroll) colGreen else colRed, "SCR")
        drawStatDot(canvas, tx + dp(50), ty, if (autoRotate) colGreen else colRed, "ROT")
        drawStatDot(canvas, tx + dp(100),ty, if (autoPip)   colGreen else colRed, "PiP")
        ty += lh * 1.3f

        // Orientation + zoom
        textPaint.color = colAmber
        canvas.drawText("${s.currentOrientation}  ×${"%.1f".format(s.currentZoom)}", tx, ty, textPaint)
        ty += lh

        // PiP badge
        if (isPip) {
            textPaint.color = colGreen
            canvas.drawText("● PiP ACTIVE", tx, ty, textPaint)
        }
    }

    private fun drawStatDot(canvas: Canvas, x: Float, y: Float, color: Int, label: String) {
        val r = dp(4)
        dotPaint.color = color
        canvas.drawCircle(x, y - r, r, dotPaint)
        textPaint.color = color
        canvas.drawText(label, x + dp(7), y, textPaint)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun dp(value: Int)   = value.toFloat() * resources.displayMetrics.density
}

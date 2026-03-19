package com.mediatest.app

import android.content.Context

/**
 * Typed wrapper around automation interval preferences (all in milliseconds).
 */
data class AutomationConfig(
    val scrollIntervalMs: Long,
    val rotateIntervalMs: Long,
    val pipIntervalMs: Long,
    val zoomIntervalMs: Long,
) {
    companion object {
        private const val PREFS = "media_test_prefs"

        fun load(ctx: Context): AutomationConfig {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AutomationConfig(
                scrollIntervalMs = p.getInt("scroll_interval", 5).toLong() * 1_000,
                rotateIntervalMs = p.getInt("rotate_interval", 8).toLong() * 1_000,
                pipIntervalMs    = p.getInt("pip_interval",    12).toLong() * 1_000,
                zoomIntervalMs   = p.getInt("zoom_interval",   3).toLong() * 1_000,
            )
        }

        fun save(ctx: Context, scrollS: Int, rotateS: Int, pipS: Int, zoomS: Int) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("scroll_interval", scrollS)
                .putInt("rotate_interval", rotateS)
                .putInt("pip_interval", pipS)
                .putInt("zoom_interval", zoomS)
                .apply()
        }
    }
}

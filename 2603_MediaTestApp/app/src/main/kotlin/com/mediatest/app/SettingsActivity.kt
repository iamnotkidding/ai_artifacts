package com.mediatest.app

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Test Settings"
        }

        val prefs = getSharedPreferences("media_test_prefs", MODE_PRIVATE)

        data class Row(val seekId: Int, val tvId: Int, val key: String,
                       val default: Int, val min: Int, val max: Int, val label: String)

        val rows = listOf(
            Row(R.id.sbScrollInterval, R.id.tvScrollVal, "scroll_interval", 5,  2,  30, "Auto-scroll"),
            Row(R.id.sbRotateInterval, R.id.tvRotateVal, "rotate_interval", 8,  3,  30, "Auto-rotate"),
            Row(R.id.sbPipInterval,    R.id.tvPipVal,    "pip_interval",    12, 5,  60, "Auto-PiP"),
            Row(R.id.sbZoomInterval,   R.id.tvZoomVal,   "zoom_interval",   3,  1,  10, "Zoom step"),
        )

        rows.forEach { row ->
            val sb = findViewById<SeekBar>(row.seekId)
            val tv = findViewById<TextView>(row.tvId)
            val current = prefs.getInt(row.key, row.default)

            sb.max      = row.max - row.min
            sb.progress = current - row.min
            tv.text     = "${row.label}: ${current}s"

            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    tv.text = "${row.label}: ${p + row.min}s"
                }
                override fun onStartTrackingTouch(s: SeekBar) = Unit
                override fun onStopTrackingTouch(s: SeekBar) = Unit
            })
        }

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val edit = prefs.edit()
            rows.forEach { row ->
                val sb  = findViewById<SeekBar>(row.seekId)
                edit.putInt(row.key, sb.progress + row.min)
            }
            edit.apply()
            Toast.makeText(this, "Saved — restart app to apply", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

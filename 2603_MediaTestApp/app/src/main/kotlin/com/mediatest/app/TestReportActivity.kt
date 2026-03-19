package com.mediatest.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_report)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Test Report"
        }

        @Suppress("DEPRECATION")
        val session = intent.getSerializableExtra("session") as? TestSession
            ?: return finish().also { }

        findViewById<TextView>(R.id.tvSummary).text = buildSummary(session)

        val logAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            session.eventLog.toList(),
        )
        findViewById<ListView>(R.id.listLog).adapter = logAdapter
    }

    private fun buildSummary(s: TestSession) = buildString {
        appendLine("━━━━ SESSION SUMMARY ━━━━")
        appendLine("Duration      : ${s.elapsedSeconds()} s")
        appendLine("Video switches: ${s.totalVideoSwitches}")
        appendLine("Auto scrolls  : ${s.totalAutoScrolls}")
        appendLine("Rotations     : ${s.totalRotations}")
        appendLine("Zoom events   : ${s.totalZoomEvents}")
        appendLine("PiP enters    : ${s.totalPipEnters}")
        appendLine("PiP exits     : ${s.totalPipExits}")
        appendLine("Load events   : ${s.totalLoadEvents}")
        appendLine("Ready events  : ${s.totalReadyEvents}")
        appendLine()
        appendLine("━━━━ FORMAT HITS ━━━━")
        s.formatHits.entries
            .sortedByDescending { it.value }
            .forEach { (fmt, count) -> appendLine("$fmt : $count") }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

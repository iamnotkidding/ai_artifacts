package com.mediatest.app

import android.graphics.Color

enum class VideoFormat(val badgeLabel: String, val badgeColor: Int) {
    MP4_H264("MP4",  Color.parseColor("#00C853")),
    MP4_H265("HEVC", Color.parseColor("#AA00FF")),
    HLS     ("HLS",  Color.parseColor("#2979FF")),
    DASH    ("DASH", Color.parseColor("#FF6D00")),
    WEBM_VP9("VP9",  Color.parseColor("#00B8D4")),
    WEBM_AV1("AV1",  Color.parseColor("#FFD600")),
}

package com.mediatest.app

import java.io.Serializable

data class VideoItem(
    val url: String,
    val title: String,
    val formatDescription: String,
    val format: VideoFormat,
) : Serializable

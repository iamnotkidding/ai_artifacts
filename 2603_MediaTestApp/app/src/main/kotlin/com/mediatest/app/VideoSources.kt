package com.mediatest.app

/**
 * Central catalogue of test video sources covering multiple formats.
 * Add / remove entries freely; the feed loops infinitely.
 */
object VideoSources {

    val items: List<VideoItem> = listOf(

        // ── MP4 / H.264 ────────────────────────────────────────────────────
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            title = "Big Buck Bunny",
            formatDescription = "MP4 · H.264 · 1080p",
            format = VideoFormat.MP4_H264,
        ),
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            title = "Elephants Dream",
            formatDescription = "MP4 · H.264 · 720p",
            format = VideoFormat.MP4_H264,
        ),
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            title = "For Bigger Blazes",
            formatDescription = "MP4 · Short Clip",
            format = VideoFormat.MP4_H264,
        ),
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            title = "For Bigger Escapes",
            formatDescription = "MP4 · Action",
            format = VideoFormat.MP4_H264,
        ),
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            title = "Subaru Outback",
            formatDescription = "MP4 · Landscape",
            format = VideoFormat.MP4_H264,
        ),
        VideoItem(
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            title = "Bullrun Short",
            formatDescription = "MP4 · Vertical",
            format = VideoFormat.MP4_H264,
        ),

        // ── HLS ────────────────────────────────────────────────────────────
        VideoItem(
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            title = "Mux HLS Stream",
            formatDescription = "HLS · Adaptive Bitrate",
            format = VideoFormat.HLS,
        ),

        // ── DASH ───────────────────────────────────────────────────────────
        VideoItem(
            url = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd",
            title = "Angel One DASH",
            formatDescription = "DASH · Multi-bitrate",
            format = VideoFormat.DASH,
        ),
    )
}

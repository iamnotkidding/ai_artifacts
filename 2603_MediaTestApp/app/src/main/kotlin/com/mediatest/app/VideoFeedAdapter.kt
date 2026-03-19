package com.mediatest.app

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class VideoFeedAdapter(
    private val context: Context,
    private val items: List<VideoItem>,
    private val session: TestSession,
) : RecyclerView.Adapter<VideoFeedAdapter.VideoViewHolder>() {

    // Sparse player pool keyed by adapter position
    private val playerPool = mutableMapOf<Int, ExoPlayer>()
    var currentPosition = 0
        private set

    // ── RecyclerView overrides ────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video_feed, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position % items.size]
        holder.bind(item, position)
        currentPosition = position
    }

    override fun getItemCount(): Int = Int.MAX_VALUE   // infinite loop

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.detachPlayer()
    }

    // ── Player pool helpers ───────────────────────────────────────────────

    private fun getOrCreatePlayer(position: Int): ExoPlayer =
        playerPool.getOrPut(position) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0.5f
            }
        }

    fun resumeCurrentPlayer() = playerPool[currentPosition]?.play()
    fun pauseCurrentPlayer()  = playerPool[currentPosition]?.pause()

    fun releaseAll() {
        playerPool.values.forEach(ExoPlayer::release)
        playerPool.clear()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ViewHolder
    // ════════════════════════════════════════════════════════════════════════

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val playerView: StyledPlayerView = itemView.findViewById(R.id.playerView)
        private val tvTitle: TextView             = itemView.findViewById(R.id.tvTitle)
        private val tvFormat: TextView            = itemView.findViewById(R.id.tvFormat)
        private val tvBitrate: TextView           = itemView.findViewById(R.id.tvBitrate)
        private val loadingView: ProgressBar      = itemView.findViewById(R.id.loadingView)
        private val tvFormatBadge: TextView       = itemView.findViewById(R.id.tvFormatBadge)

        fun bind(item: VideoItem, position: Int) {
            tvTitle.text       = item.title
            tvFormat.text      = item.formatDescription
            tvFormatBadge.text = item.format.badgeLabel
            tvFormatBadge.setBackgroundColor(item.format.badgeColor)

            val player = getOrCreatePlayer(position)
            playerView.player = player

            if (player.mediaItemCount == 0) {
                player.setMediaSource(buildMediaSource(item))
                player.prepare()
                session.recordVideoLoad(item)
            }

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    loadingView.visibility =
                        if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY) {
                        session.recordVideoReady(item)
                        // Show bitrate
                        val kbps = (player.videoFormat?.bitrate ?: 0) / 1000
                        tvBitrate.text = if (kbps > 0) "${kbps} kbps" else ""
                    }
                }
            })

            player.play()

            // Entrance fade
            itemView.alpha = 0f
            itemView.animate().alpha(1f).setDuration(300).start()
        }

        fun detachPlayer() {
            playerView.player = null
        }

        /** Smoothly animate zoom on the PlayerView surface. */
        fun applyZoom(scale: Float) {
            listOf(
                ObjectAnimator.ofFloat(playerView, View.SCALE_X, playerView.scaleX, scale),
                ObjectAnimator.ofFloat(playerView, View.SCALE_Y, playerView.scaleY, scale),
            ).forEach { anim ->
                anim.duration = 600
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.start()
            }
        }

        // ── Media source factory ─────────────────────────────────────────

        private fun buildMediaSource(item: VideoItem): MediaSource {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("MediaTestApp/1.0")
                .setAllowCrossProtocolRedirects(true)
            val dataFactory = DefaultDataSource.Factory(context, httpFactory)
            val mediaItem   = MediaItem.fromUri(item.url)

            return when (item.format) {
                VideoFormat.HLS  -> HlsMediaSource.Factory(dataFactory).createMediaSource(mediaItem)
                VideoFormat.DASH -> DashMediaSource.Factory(dataFactory).createMediaSource(mediaItem)
                else             -> ProgressiveMediaSource.Factory(dataFactory).createMediaSource(mediaItem)
            }
        }
    }
}

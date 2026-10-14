package com.kas.tv

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        val name = intent.getStringExtra("name") ?: "KAS TV"
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val pv = PlayerView(this)
        frame.addView(pv, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this).apply {
            text = name; textSize = 18f; setTextColor(-1); setPadding(18, 12, 18, 12)
            setBackgroundColor(0x99000000.toInt())
        }
        frame.addView(title, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP))
        setContentView(frame)
        if (url.isNullOrBlank()) { title.text = "No stream URL"; return }
        try {
            player = ExoPlayer.Builder(this).build().also { exo ->
                pv.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        title.text = "Playback error: ${error.errorCodeName}"
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) title.visibility = View.GONE
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(url))
                exo.prepare()
                exo.playWhenReady = true
            }
        } catch (e: Exception) { title.text = "Player error: ${e.message}" }
    }
    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}

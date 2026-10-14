package com.kas.tv

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var title: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url")?.trim()
        val name = intent.getStringExtra("name") ?: "KAS TV"

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(-1, -1)
        )

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        root.addView(
            progressBar,
            FrameLayout.LayoutParams(
                -2,
                -2,
                Gravity.CENTER
            )
        )

        title = TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(20, 16, 20, 16)
            setBackgroundColor(0xCC000000.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(
            title,
            FrameLayout.LayoutParams(
                -1,
                -2,
                Gravity.TOP
            )
        )

        setContentView(root)

        if (url.isNullOrBlank()) {
            showError("URL کەناڵەکە بەتاڵە")
            return
        }

        playChannel(url, name)
    }

    private fun playChannel(url: String, name: String) {

        try {
            title.visibility = View.VISIBLE
            title.text = "$name\nچاوەڕێ بکە..."
            progressBar.visibility = View.VISIBLE

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
                )
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory =
                DefaultDataSource.Factory(this, httpFactory)

            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        dataSourceFactory
                    )
                )
                .build()

            playerView.player = player

            player?.addListener(object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    when (playbackState) {

                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                            title.visibility = View.VISIBLE
                            title.text = "$name\nبارکردنی کەناڵ..."
                        }

                        Player.STATE_READY -> {
                            progressBar.visibility = View.GONE
                            title.visibility = View.GONE
                        }

                        Player.STATE_ENDED -> {
                            progressBar.visibility = View.GONE
                            title.visibility = View.VISIBLE
                            title.text = "$name\nپەخشکردن تەواو بوو"
                        }

                        Player.STATE_IDLE -> {
                            progressBar.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    progressBar.visibility = View.GONE
                    title.visibility = View.VISIBLE

                    val details = when (
                        error.errorCode
                    ) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                            "پەیوەندی ئینتەرنێت سەرکەوتوو نەبوو"

                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            "کاتی چاوەڕوانی پەیوەندی تەواو بوو"

                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                            "سێرڤەر وەڵامی HTTP ی نادروستی دا"

                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                            "فۆرماتی ڤیدیۆکە نادروستە"

                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                            "فۆرماتی ڤیدیۆکە پشتگیری ناکرێت"

                        else ->
                            error.errorCodeName
                    }

                    title.text =
                        "$name\nPlayer error: $details"
                }
            })

            val lowerUrl = url.lowercase()

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(url)

            if (
                lowerUrl.substringBefore("?")
                    .endsWith(".m3u8")
            ) {
                mediaItemBuilder.setMimeType(
                    MimeTypes.APPLICATION_M3U8
                )
            }

            player?.setMediaItem(mediaItemBuilder.build())
            player?.prepare()
            player?.playWhenReady = true

        } catch (e: Exception) {
            showError(
                "Player error: ${
                    e.localizedMessage ?: "Unknown error"
                }"
            )
        }
    }

    private fun showError(message: String) {
        if (::progressBar.isInitialized) {
            progressBar.visibility = View.GONE
        }

        if (::title.isInitialized) {
            title.visibility = View.VISIBLE
            title.text = message
        }
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

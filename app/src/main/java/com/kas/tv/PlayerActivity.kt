package com.kas.tv

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var channelPanel: LinearLayout
    private lateinit var controlsBar: LinearLayout
    private lateinit var listToggle: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val bgColor = Color.rgb(8, 9, 13)
    private val cardColor = Color.rgb(28, 30, 39)
    private val purple = Color.rgb(142, 68, 173)
    private val white = Color.WHITE
    private val grey = Color.rgb(185, 190, 202)

    private var currentIndex = 0
    private var channelNames = arrayListOf<String>()
    private var channelUrls = arrayListOf<String>()
    private var currentName = "KAS TV"

    private val hideOverlays = Runnable { hideTemporaryOverlays() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterFullscreen()

        currentName = intent.getStringExtra("name") ?: "KAS TV"
        val initialUrl = intent.getStringExtra("url")?.trim().orEmpty()
        channelNames = intent.getStringArrayListExtra("channel_names") ?: arrayListOf(currentName)
        channelUrls = intent.getStringArrayListExtra("channel_urls") ?: arrayListOf(initialUrl)
        channelUrls = ArrayList(channelUrls.filter { it.isNotBlank() })
        if (channelNames.size != channelUrls.size) {
            channelNames = ArrayList(channelUrls.indices.map { channelNames.getOrNull(it) ?: "Channel ${it + 1}" })
        }
        currentIndex = intent.getIntExtra("selected_index", 0).coerceIn(0, (channelUrls.size - 1).coerceAtLeast(0))

        if (channelUrls.isEmpty() || initialUrl.isBlank()) {
            showError("URL ـی کەناڵەکە بەتاڵە")
            return
        }

        buildPlayerLayout()
        startPlaylistPlayback()
        showTemporaryOverlays()
    }

    @Suppress("DEPRECATION")
    private fun enterFullscreen() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun buildPlayerLayout() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        titleView = TextView(this).apply {
            text = currentName
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(0xCC000000.toInt(), 8)
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(titleView, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(10), dp(72), 0)
        })

        progressBar = ProgressBar(this).apply { visibility = View.VISIBLE }
        root.addView(progressBar, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

        listToggle = TextView(this).apply {
            text = "☰"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
            background = rounded(0xCC000000.toInt(), 30)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (channelPanel.visibility == View.VISIBLE) {
                    channelPanel.visibility = View.GONE
                    scheduleHide()
                } else {
                    channelPanel.visibility = View.VISIBLE
                    titleView.visibility = View.VISIBLE
                    controlsBar.visibility = View.VISIBLE
                    renderChannelList()
                    scheduleHide()
                }
            }
        }
        root.addView(listToggle, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(10), dp(10), 0)
        })

        channelPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(0xE8171922.toInt(), 12)
            visibility = View.VISIBLE
        }
        val panelHeader = TextView(this).apply {
            text = "📺  کەناڵەکان"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(10))
        }
        channelPanel.addView(panelHeader, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val channelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        channelList.tag = "kas_channel_list"
        scroll.addView(channelList, ViewGroup.LayoutParams(-1, -2))
        channelPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(channelPanel, FrameLayout.LayoutParams(dp(265), -1, Gravity.END).apply {
            setMargins(0, dp(66), dp(10), dp(72))
        })

        controlsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(0xCC000000.toInt(), 12)
        }
        controlsBar.addView(controlButton("⏮  Previous") { playPrevious() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        controlsBar.addView(controlButton("Next  ⏭") { playNext() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        root.addView(controlsBar, FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dp(8), 0, dp(8), dp(12))
        })

        playerView.setOnClickListener { showTemporaryOverlays() }
        setContentView(root)
        renderChannelList()
    }

    private fun renderChannelList() {
        if (!::channelPanel.isInitialized) return
        val list = channelPanel.findViewWithTag<LinearLayout>("kas_channel_list") ?: return
        list.removeAllViews()
        channelUrls.forEachIndexed { index, url ->
            if (url.isBlank()) return@forEachIndexed
            val name = channelNames.getOrNull(index)?.ifBlank { "Channel ${index + 1}" } ?: "Channel ${index + 1}"
            val selected = index == currentIndex
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = rounded(if (selected) purple else cardColor, 8)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentIndex = index
                    player?.seekTo(index, 0L)
                    player?.playWhenReady = true
                    currentName = name
                    titleView.text = name
                    renderChannelList()
                    showTemporaryOverlays()
                }
            }
            row.addView(TextView(this).apply {
                text = if (selected) "▶" else "▷"
                textSize = 16f
                setTextColor(white)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(25), -2))
            row.addView(TextView(this).apply {
                text = name
                textSize = 13f
                maxLines = 2
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(white)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun startPlaylistPlayback() {
        try {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("KAS-TV Android Player")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(25000)
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
            val exo = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
            player = exo
            playerView.player = exo

            exo.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = exo.currentMediaItemIndex
                    if (index in channelUrls.indices) {
                        currentIndex = index
                        currentName = channelNames.getOrNull(index) ?: "Channel ${index + 1}"
                        titleView.text = currentName
                        renderChannelList()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                            titleView.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            progressBar.visibility = View.GONE
                            scheduleHide()
                        }
                        Player.STATE_ENDED -> {
                            progressBar.visibility = View.GONE
                            showTemporaryOverlays()
                        }
                        Player.STATE_IDLE -> progressBar.visibility = View.GONE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    progressBar.visibility = View.GONE
                    titleView.visibility = View.VISIBLE
                    titleView.text = "$currentName\nپەخشکردن سەرکەوتوو نەبوو: ${error.errorCodeName}"
                    showTemporaryOverlays()
                }
            })

            val mediaItems = channelUrls.map { url ->
                val cleanUrl = url.substringBefore("?").lowercase()
                val builder = MediaItem.Builder().setUri(url)
                if (cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".m3u")) {
                    builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                builder.build()
            }
            exo.setMediaItems(mediaItems, currentIndex, 0L)
            exo.prepare()
            exo.playWhenReady = true
        } catch (e: Exception) {
            showError("هەڵە لە پەخشکردن: ${e.localizedMessage ?: "هەڵەی نەناسراو"}")
        }
    }

    private fun playNext() {
        val exo = player ?: return
        if (channelUrls.isEmpty()) return
        if (exo.currentMediaItemIndex < channelUrls.lastIndex) exo.seekToNextMediaItem() else exo.seekTo(0, 0L)
        exo.playWhenReady = true
        showTemporaryOverlays()
    }

    private fun playPrevious() {
        val exo = player ?: return
        if (channelUrls.isEmpty()) return
        if (exo.currentMediaItemIndex > 0) exo.seekToPreviousMediaItem() else exo.seekTo(channelUrls.lastIndex, 0L)
        exo.playWhenReady = true
        showTemporaryOverlays()
    }

    private fun showTemporaryOverlays() {
        if (!::channelPanel.isInitialized) return
        channelPanel.visibility = View.VISIBLE
        controlsBar.visibility = View.VISIBLE
        titleView.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideOverlays)
        handler.postDelayed(hideOverlays, 1000L)
    }

    private fun hideTemporaryOverlays() {
        if (!::channelPanel.isInitialized) return
        channelPanel.visibility = View.GONE
        controlsBar.visibility = View.GONE
        titleView.visibility = View.GONE
        // The small menu icon remains so the channel list can be opened again.
        listToggle.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        setContentView(TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.BLACK)
        })
    }

    private fun controlButton(textValue: String, action: () -> Unit) = TextView(this).apply {
        text = textValue
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        gravity = Gravity.CENTER
        background = rounded(0xCC252735.toInt(), 8)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::playerView.isInitialized) playerView.player = null
        player?.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

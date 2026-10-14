package com.kas.tv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var title: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var channelList: LinearLayout

    private val bgColor = Color.rgb(10, 11, 15)
    private val cardColor = Color.rgb(29, 31, 39)
    private val gold = Color.rgb(245, 180, 0)
    private val white = Color.WHITE
    private val grey = Color.rgb(175, 179, 190)

    private var currentIndex = 0

    private var channelNames = arrayListOf<String>()
    private var channelUrls = arrayListOf<String>()

    private var currentName = "KAS TV"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enterFullscreen()

        currentName =
            intent.getStringExtra("name") ?: "KAS TV"

        val url =
            intent.getStringExtra("url")?.trim()

        channelNames =
            intent.getStringArrayListExtra("channel_names")
                ?: arrayListOf(currentName)

        channelUrls =
            intent.getStringArrayListExtra("channel_urls")
                ?: arrayListOf(url ?: "")

        currentIndex =
            intent.getIntExtra("selected_index", 0)
                .coerceIn(0, (channelUrls.size - 1).coerceAtLeast(0))

        if (url.isNullOrBlank()) {
            showError("URL کەناڵەکە بەتاڵە")
            return
        }

        buildPlayerLayout()
        playChannel(url, currentName)
    }

    private fun enterFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun buildPlayerLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
        }

        // لای چەپ: پلەیەر
        val videoArea = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(Color.BLACK)
        }

        videoArea.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        title = TextView(this).apply {
            text = currentName
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xCC000000.toInt(), 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        videoArea.addView(
            title,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(dp(8), dp(8), dp(8), 0)
            }
        )

        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
        }

        videoArea.addView(
            progressBar,
            FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                Gravity.CENTER
            )
        )

        root.addView(
            videoArea,
            LinearLayout.LayoutParams(0, -1, 2.5f)
        )

        // لای ڕاست: لیستی کەناڵەکان
        val sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }

        header.addView(
            TextView(this).apply {
                text = "📺  کەناڵەکان"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(
            TextView(this).apply {
                text = "${channelUrls.size}"
                textSize = 13f
                setTextColor(grey)
            }
        )

        sidePanel.addView(header)

        val divider = View(this).apply {
            setBackgroundColor(cardColor)
        }

        sidePanel.addView(
            divider,
            LinearLayout.LayoutParams(-1, dp(1))
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            channelList,
            ScrollView.LayoutParams(-1, -2)
        )

        sidePanel.addView(
            scroll,
            LinearLayout.LayoutParams( -1, 0, 1f)
        )

        root.addView(
            sidePanel,
            LinearLayout.LayoutParams(0, -1, 1f)
        )

        setContentView(root)

        renderChannelList()
    }

    private fun renderChannelList() {
        if (!::channelList.isInitialized) return

        channelList.removeAllViews()

        channelUrls.forEachIndexed { index, url ->
            if (url.isBlank()) return@forEachIndexed

            val name = channelNames.getOrNull(index)
                ?: "Channel ${index + 1}"

            val selected = index == currentIndex

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = rounded(
                    if (selected) gold else cardColor,
                    8
                )
                isClickable = true
                isFocusable = true
                contentDescription = name

                setOnClickListener {
                    currentIndex = index
                    currentName = name

                    renderChannelList()
                    playChannel(url, name)
                }
            }

            card.addView(
                TextView(this).apply {
                    text = if (selected) "▶" else "▷"
                    textSize = 17f
                    setTextColor(
                        if (selected) Color.BLACK else gold
                    )
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(dp(28), -2)
            )

            card.addView(
                TextView(this).apply {
                    text = name
                    textSize = 13f
                    maxLines = 2
                    setTextColor(
                        if (selected) Color.BLACK else white
                    )
                    typeface = if (selected) {
                        Typeface.DEFAULT_BOLD
                    } else {
                        Typeface.DEFAULT
                    }
                },
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            channelList.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            )
        }
    }

    private fun playChannel(url: String, name: String) {
        try {
            title.visibility = View.VISIBLE
            title.text = "$name\nبارکردنی کەناڵ..."
            progressBar.visibility = View.VISIBLE

            // یاریزانی پێشوو ئازاد دەکەین
            playerView.player = null
            player?.release()
            player = null

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
                )
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory =
                DefaultDataSource.Factory(this, httpFactory)

            val newPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        dataSourceFactory
                    )
                )
                .build()

            player = newPlayer
            playerView.player = newPlayer

            newPlayer.addListener(object : Player.Listener {

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

                    val details = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                            "پەیوەندی ئینتەرنێت سەرکەوتوو نەبوو"

                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            "کاتی چاوەڕوانی پەیوەندی تەواو بوو"

                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                            "سێرڤەر وەڵامی HTTP ـی نادروستی دا"

                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                            "فۆرماتی ڤیدیۆکە نادروستە"

                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                            "فۆرماتی ڤیدیۆکە پشتگیری ناکرێت"

                        else -> error.errorCodeName
                    }

                    title.text = "$name\n$details"
                }
            })

            val mediaItemBuilder =
                MediaItem.Builder().setUri(url)

            if (
                url.substringBefore("?")
                    .lowercase()
                    .endsWith(".m3u8")
            ) {
                mediaItemBuilder.setMimeType(
                    MimeTypes.APPLICATION_M3U8
                )
            }

            newPlayer.setMediaItem(mediaItemBuilder.build())
            newPlayer.prepare()
            newPlayer.playWhenReady = true

        } catch (e: Exception) {
            showError(
                "Player error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun showError(message: String) {
        val errorText = TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.BLACK)
        }

        setContentView(errorText)
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        if (::playerView.isInitialized) {
            playerView.player = null
        }

        player?.release()
        player = null

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onDestroy()
    }
}

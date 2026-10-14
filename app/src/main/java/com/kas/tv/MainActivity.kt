package com.kas.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class TvItem(
    val name: String,
    val url: String,
    val group: String = ""
)

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<TvItem>()

    private var playlistText = ""
    private var lastUrl = ""
    private var selectedTab = "Live TV"

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var refreshIcon: TextView
    private lateinit var sourceInput: EditText
    private lateinit var serverInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var playlistPanel: LinearLayout

    private var spinner: ObjectAnimator? = null

    private val gold = Color.rgb(245, 180, 0)
    private val bgColor = Color.rgb(12, 13, 17)
    private val cardColor = Color.rgb(29, 31, 39)
    private val white = Color.WHITE
    private val grey = Color.rgb(175, 179, 190)

    private val pickM3u =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use {
                            lastUrl = ""
                            playlistText = it.readText()
                            parsePlaylist(playlistText)
                        } ?: showStatus("نەتوانرا فایلەکە بخوێندرێتەوە")
                } catch (e: Exception) {
                    showStatus("هەڵە لە خوێندنەوەی فایل")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        buildScreen()
        showStatus("بەخێربێیت بۆ KAS TV")
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(20))
            setBackgroundColor(bgColor)
        }

        scroll.addView(page)
        setContentView(scroll)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "KAS TV"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            gravity = Gravity.CENTER_VERTICAL
        }

        val subtitle = TextView(this).apply {
            text = "  |  YOUR ENTERTAINMENT"
            textSize = 12f
            setTextColor(grey)
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(55)
            )
        )

        header.addView(
            subtitle,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        page.addView(header)

        // Navigation tabs
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val tabNames = listOf(
            "📡  Live TV",
            "🎬  Movies",
            "📂  Series",
            "📋  Playlist"
        )

        tabNames.forEach { label ->
            val name = label.substringAfter("  ")

            val button = TextView(this).apply {
                text = label
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(13), dp(8), dp(13))
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    selectedTab = name
                    updateTabs(tabs, tabNames)
                    renderContent()
                }
            }

            button.tag = name

            val params = LinearLayout.LayoutParams(
                0,
                dp(54),
                1f
            )

            params.setMargins(dp(4), dp(4), dp(4), dp(4))
            tabs.addView(button, params)
        }

        page.addView(tabs)
        updateTabs(tabs, tabNames)

        // Refresh button
        val refreshButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(gold, 12)
            isFocusable = true
            isClickable = true
        }

        refreshIcon = TextView(this).apply {
            text = "⟳"
            textSize = 26f
            setTextColor(Color.BLACK)
        }

        val refreshLabel = TextView(this).apply {
            text = "  Refresh Playlist"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
        }

        refreshButton.addView(refreshIcon)
        refreshButton.addView(refreshLabel)

        refreshButton.setOnClickListener {
            refreshPlaylist()
        }

        val refreshRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        refreshRow.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
        )

        page.addView(refreshRow)

        // Status
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(grey)
            setPadding(dp(4), dp(5), dp(4), dp(8))
        }

        page.addView(status)

        // Main content
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        page.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Playlist panel
        playlistPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        sourceInput = makeInput("M3U / M3U8 URL")
        serverInput = makeInput("Xtream Server URL")
        usernameInput = makeInput("Username")
        passwordInput = makeInput("Password")

        passwordInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        addField(playlistPanel, sourceInput)

        addActionButton(playlistPanel, "Load M3U URL") {
            loadUrl(sourceInput.text.toString().trim())
        }

        addActionButton(playlistPanel, "📁  Choose M3U File") {
            pickM3u.launch(arrayOf("*/*"))
        }

        addHeading(playlistPanel, "Xtream Codes")

        addField(playlistPanel, serverInput)
        addField(playlistPanel, usernameInput)
        addField(playlistPanel, passwordInput)

        addActionButton(playlistPanel, "Connect Xtream") {
            val server = serverInput.text.toString()
                .trim()
                .removeSuffix("/")

            val user = usernameInput.text.toString().trim()
            val pass = passwordInput.text.toString()

            if (
                server.isBlank() ||
                user.isBlank() ||
                pass.isBlank()
            ) {
                showStatus(
                    "تکایە Server و Username و Password پڕ بکەرەوە."
                )
            } else {
                val url = "$server/get.php?username=${Uri.encode(user)}" +
                    "&password=${Uri.encode(pass)}" +
                    "&type=m3u_plus&output=ts"

                sourceInput.setText(url)
                loadUrl(url)
            }
        }

        addHeading(playlistPanel, "MAC / Portal")

        addLabel(
            playlistPanel,
            "بەشی MAC / Portal لەم وەشانەدا چالاک نییە."
        )

        renderContent()
    }

    private fun updateTabs(
        tabs: LinearLayout,
        labels: List<String>
    ) {
        for (i in 0 until tabs.childCount) {
            val button = tabs.getChildAt(i) as TextView
            val name = button.tag as String
            val active = name == selectedTab

            button.text = labels[i]
            button.setTextColor(
                if (active) Color.BLACK else white
            )
            button.background = rounded(
                if (active) gold else cardColor,
                12
            )
        }
    }

    private fun renderContent() {
        content.removeAllViews()

        if (selectedTab == "Playlist") {
            content.addView(playlistPanel, matchWidth())
            return
        }

        val heading = TextView(this).apply {
            text = when (selectedTab) {
                "Movies" -> "🎬  Movies"
                "Series" -> "📂  Series"
                else -> "📡  Live TV"
            }
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }

        content.addView(heading)

        val filtered = items.filter { item ->
            val group = item.group.lowercase()
            val name = item.name.lowercase()

            val isMovie =
                group.contains("movie") ||
                group.contains("film") ||
                group.contains("cinema") ||
                group.contains("vod") ||
                name.contains("movie") ||
                name.contains("film")

            val isSeries =
                group.contains("series") ||
                group.contains("serial") ||
                group.contains("drama") ||
                group.contains("show") ||
                name.contains("season ") ||
                name.contains("episode ")

            when (selectedTab) {
                "Movies" -> isMovie
                "Series" -> isSeries
                else -> !isMovie && !isSeries
            }
        }

        if (filtered.isEmpty()) {
            addLabel(
                content,
                if (items.isEmpty()) {
                    "هیچ کەناڵێک بار نەکراوە. بچۆ Playlist."
                } else {
                    "هیچ بابەتێک لەم بەشەدا نەدۆزرایەوە."
                }
            )
            return
        }

        var index = 0

        while (index < filtered.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            repeat(2) {
                if (index < filtered.size) {
                    val item = filtered[index]

                    row.addView(
                        makeCard(item),
                        LinearLayout.LayoutParams(
                            0,
                            dp(78),
                            1f
                        ).apply {
                            setMargins(
                                dp(4),
                                dp(4),
                                dp(4),
                                dp(4)
                            )
                        }
                    )

                    index++
                } else {
                    row.addView(
                        View(this@MainActivity),
                        LinearLayout.LayoutParams(
                            0,
                            dp(78),
                            1f
                        )
                    )
                }
            }

            content.addView(row)
        }
    }

    private fun makeCard(item: TvItem): TextView {
        return TextView(this).apply {
            text = "▶   ${item.name}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            setTextColor(white)
            background = rounded(cardColor, 12)
            isFocusable = true
            isClickable = true

            setOnFocusChangeListener { view, focused ->
                view.background = rounded(
                    if (focused) gold else cardColor,
                    12
                )

                (view as TextView).setTextColor(
                    if (focused) Color.BLACK else white
                )
            }

            setOnClickListener {
                val intent = Intent(
                    this@MainActivity,
                    PlayerActivity::class.java
                )

                intent.putExtra("url", item.url)
                intent.putExtra("name", item.name)

                startActivity(intent)
            }
        }
    }

    private fun refreshPlaylist() {
        if (spinner?.isRunning == true) return

        if (lastUrl.isNotBlank()) {
            loadUrl(lastUrl)
        } else if (playlistText.isNotBlank()) {
            startSpinner()

            parsePlaylist(playlistText)

            stopSpinner()
            showStatus("Playlist نوێ کرایەوە.")
        } else {
            showStatus(
                "سەرەتا لە Playlist ـەوە لیستێک بار بکە."
            )
        }
    }

    private fun startSpinner() {
        spinner?.cancel()

        spinner = ObjectAnimator.ofFloat(
            refreshIcon,
            "rotation",
            0f,
            360f
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopSpinner() {
        spinner?.cancel()
        spinner = null

        if (::refreshIcon.isInitialized) {
            refreshIcon.rotation = 0f
        }
    }

    private fun loadUrl(url: String) {
        if (
            !url.startsWith("http://") &&
            !url.startsWith("https://")
        ) {
            showStatus(
                "URL ـێکی دروستی http:// یان https:// بنووسە."
            )
            return
        }

        lastUrl = url
        showStatus("Playlist دەخوێندرێتەوە...")

        startSpinner()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val connection =
                        URL(url).openConnection() as HttpURLConnection

                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000

                    connection.setRequestProperty(
                        "User-Agent",
                        "KAS-TV/1.0"
                    )

                    try {
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    } finally {
                        connection.disconnect()
                    }
                }

                playlistText = result
                parsePlaylist(result)

                showStatus(
                    "Playlist بار کرا. ژمارەی بابەتەکان: ${items.size}"
                )
            } catch (e: Exception) {
                showStatus(
                    "نەتوانرا Playlist بار بکرێت. URL ـەکە بپشکنە."
                )
            } finally {
                stopSpinner()
            }
        }
    }

    private fun parsePlaylist(text: String) {
        items.clear()

        var name = "Channel"
        var group = ""

        text.lineSequence().forEach { raw ->
            val line = raw.trim()

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                name = line.substringAfterLast(",", "Channel")
                    .trim()
                    .ifBlank { "Channel" }

                val tvgName = Regex("""tvg-name="([^"]+)"""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)

                if (!tvgName.isNullOrBlank()) {
                    name = tvgName
                }

                group = Regex("""group-title="([^"]+)"""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1) ?: ""
            } else if (
                line.startsWith("http://") ||
                line.startsWith("https://")
            ) {
                items.add(
                    TvItem(
                        name = name,
                        url = line,
                        group = group
                    )
                )

                name = "Channel"
                group = ""
            }
        }

        renderContent()

        showStatus(
            "ژمارەی بابەتە بارکراوەکان: ${items.size}"
        )
    }

    private fun makeInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(grey)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(cardColor, 8)
        }
    }

    private fun addField(
        parent: LinearLayout,
        field: EditText
    ) {
        val params = matchWidth()

        params.setMargins(dp(3), dp(5), dp(3), dp(5))

        parent.addView(field, params)
    }

    private fun addHeading(
        parent: LinearLayout,
        text: String
    ) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
                setPadding(dp(4), dp(12), dp(4), dp(5))
            }
        )
    }

    private fun addLabel(
        parent: LinearLayout,
        text: String
    ) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(grey)
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
        )
    }

    private fun addActionButton(
        parent: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        val button = TextView(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = rounded(gold, 10)
            isFocusable = true
            isClickable = true

            setPadding(dp(12), dp(12), dp(12), dp(12))

            setOnClickListener {
                action()
            }
        }

        val params = matchWidth()

        params.setMargins(dp(3), dp(5), dp(3), dp(5))

        parent.addView(button, params)
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

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun showStatus(message: String) {
        if (::status.isInitialized) {
            status.text = message
        }
    }

    override fun onDestroy() {
        stopSpinner()
        super.onDestroy()
    }
}

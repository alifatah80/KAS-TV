package com.kas.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class TvItem(
    val name: String,
    val url: String,
    val group: String = ""
)

data class PlaylistSource(
    val id: String,
    var name: String,
    var type: String,
    var value: String,
    var extra: String = ""
)

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<TvItem>()
    private val sources = mutableListOf<PlaylistSource>()

    private var playlistText = ""
    private var lastUrl = ""
    private var selectedTab = "Live TV"
    private var showingPlaylistPage = false

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var sourceList: LinearLayout
    private lateinit var refreshIcon: TextView

    private var spinner: ObjectAnimator? = null

    private val gold = Color.rgb(245, 180, 0)
    private val bgColor = Color.rgb(12, 13, 17)
    private val cardColor = Color.rgb(29, 31, 39)
    private val white = Color.WHITE
    private val grey = Color.rgb(175, 179, 190)

    private val prefs by lazy {
        getSharedPreferences("kas_tv_sources", MODE_PRIVATE)
    }

    private val pickM3u =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importPlaylistFile(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        loadSources()
        buildHome()
        showStatus("بەخێربێیت بۆ KAS TV")
    }

    // --------------------------------------------------
    // HOME PAGE
    // --------------------------------------------------

    private fun buildHome() {
        showingPlaylistPage = false

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(20))
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
            addView(root)
        }

        setContentView(scroll)

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

        header.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(55)
            )
        )

        header.addView(
            TextView(this).apply {
                text = "  |  YOUR ENTERTAINMENT"
                textSize = 12f
                setTextColor(grey)
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        root.addView(header)

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
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(12), dp(5), dp(12))
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    if (name == "Playlist") {
                        buildPlaylistPage()
                    } else {
                        selectedTab = name
                        updateTabs(tabs, tabNames)
                        renderContent()
                    }
                }
            }

            button.tag = name

            tabs.addView(
                button,
                LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    setMargins(dp(4), dp(5), dp(4), dp(5))
                }
            )
        }

        root.addView(tabs)
        updateTabs(tabs, tabNames)

        val refreshButton = TextView(this).apply {
            text = "⟳  Refresh Playlist"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(gold, 10)
            isFocusable = true
            isClickable = true
            setOnClickListener {
                refreshPlaylist()
            }
        }

        root.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(46)
            ).apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )

        refreshIcon = refreshButton

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(grey)
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }

        root.addView(status)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            content,
            LinearLayout.LayoutParams(-1, -2)
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

    // --------------------------------------------------
    // PLAYLIST PAGE
    // --------------------------------------------------

    private fun buildPlaylistPage() {
        showingPlaylistPage = true

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
            addView(root)
        }

        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = makeButton("‹  Back", cardColor, white) {
            buildHome()
            showStatus("بەخێربێیتەوە بۆ KAS TV")
        }

        header.addView(
            back,
            LinearLayout.LayoutParams(
                dp(100),
                dp(48)
            )
        )

        header.addView(
            TextView(this).apply {
                text = "  PLAYLIST MANAGER"
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, dp(50), 1f)
        )

        root.addView(header)

        status = TextView(this).apply {
            text = "سەرچاوەکانت لێرە زیاد و بەڕێوە ببە."
            textSize = 14f
            setTextColor(grey)
            setPadding(dp(4), dp(8), dp(4), dp(10))
        }

        root.addView(status)

        // M3U URL section

        addHeading(root, "🌐  M3U / M3U8 URL")

        val nameInput = makeInput("ناوی Playlist")
        val urlInput = makeInput("https://example.com/playlist.m3u")

        root.addView(nameInput, fieldParams())
        root.addView(urlInput, fieldParams())

        addButton(root, "＋  Add Playlist URL") {
            val name = nameInput.text.toString().trim()
            val url = urlInput.text.toString().trim()

            if (!isHttpUrl(url)) {
                showStatus("تکایە URL ـێکی دروستی HTTP یان HTTPS بنووسە.")
                return@addButton
            }

            saveSource(
                name = name.ifBlank {
                    "Playlist ${sources.size + 1}"
                },
                type = "url",
                value = url
            )

            nameInput.text.clear()
            urlInput.text.clear()

            showStatus("Playlist زیاد و پاشەکەوت کرا.")
            renderSources()
        }

        // File section

        addHeading(root, "📁  M3U Playlist Files")

        addButton(root, "📂  Choose M3U File") {
            pickM3u.launch(arrayOf("*/*"))
        }

        // Xtream section

        addHeading(root, "📺  Xtream Codes")

        val xtreamName = makeInput("ناوی ئەم سەرچاوەیە")
        val server = makeInput("Server URL")
        val username = makeInput("Username")
        val password = makeInput("Password")

        password.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        root.addView(xtreamName, fieldParams())
        root.addView(server, fieldParams())
        root.addView(username, fieldParams())
        root.addView(password, fieldParams())

        addButton(root, "＋  Save & Connect Xtream") {
            val base = server.text.toString()
                .trim()
                .trimEnd('/')

            val user = username.text.toString().trim()
            val pass = password.text.toString()

            if (!isHttpUrl(base) || user.isBlank() || pass.isBlank()) {
                showStatus(
                    "Server URL و Username و Password بپشکنە."
                )
                return@addButton
            }

            val url =
                "$base/get.php?username=${Uri.encode(user)}" +
                "&password=${Uri.encode(pass)}" +
                "&type=m3u_plus&output=ts"

            saveSource(
                name = xtreamName.text.toString().trim()
                    .ifBlank { "Xtream - $user" },
                type = "url",
                value = url
            )

            showStatus("Xtream پاشەکەوت کرا؛ ئێستا Playlist دەخوێندرێتەوە.")
            loadUrl(url)
        }

        // MAC / Portal section

        addHeading(root, "🔐  MAC / Stalker Portal")

        val portalName = makeInput("ناوی Portal")
        val portalUrl = makeInput("Portal URL")
        val macInput = makeInput("MAC Address")

        root.addView(portalName, fieldParams())
        root.addView(portalUrl, fieldParams())
        root.addView(macInput, fieldParams())

        addButton(root, "＋  Save Portal / MAC") {
            val portal = portalUrl.text.toString().trim()
            val mac = macInput.text.toString().trim()
            val name = portalName.text.toString().trim()

            if (!isHttpUrl(portal) || mac.isBlank()) {
                showStatus("Portal URL و MAC Address پڕ بکەرەوە.")
                return@addButton
            }

            saveSource(
                name = name.ifBlank { "Portal - $mac" },
                type = "portal",
                value = portal,
                extra = mac
            )

            showStatus(
                "Portal پاشەکەوت کرا. پەخشکردنی Stalker هێشتا پێویستی بە API ـی تایبەتی هەیە."
            )

            portalName.text.clear()
            portalUrl.text.clear()
            macInput.text.clear()

            renderSources()
        }

        // Saved sources

        addHeading(root, "📚  Saved Playlists & Sources")

        sourceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(sourceList)

        renderSources()
    }

    private fun renderSources() {
        if (!::sourceList.isInitialized || !showingPlaylistPage) {
            return
        }

        sourceList.removeAllViews()

        if (sources.isEmpty()) {
            addLabel(
                sourceList,
                "هیچ سەرچاوەیەک زیاد نەکراوە. سەرەتا URL یان فایلێک زیاد بکە."
            )
            return
        }

        sources.toList().forEach { source ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(cardColor, 12)
            }

            box.addView(
                TextView(this).apply {
                    text = source.name
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(gold)
                }
            )

            val detail = when (source.type) {
                "url" -> "M3U / M3U8 / Xtream"
                "file" -> "M3U File"
                "portal" -> "Stalker Portal / MAC"
                else -> source.type
            }

            box.addView(
                TextView(this).apply {
                    text = detail
                    textSize = 13f
                    setTextColor(grey)
                    setPadding(0, dp(4), 0, dp(8))
                }
            )

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val useButton = makeButton(
                if (source.type == "portal") "Portal Info" else "Open",
                gold,
                Color.BLACK
            ) {
                when (source.type) {
                    "url" -> loadUrl(source.value)

                    "file" -> loadSavedFile(source)

                    "portal" -> showStatus(
                        "MAC: ${source.extra} — پاشەکەوت کراوە، بەڵام Stalker API هێشتا جێبەجێ نەکراوە."
                    )
                }
            }

            val editButton = makeButton(
                "Edit",
                Color.rgb(60, 70, 85),
                white
            ) {
                editSource(source)
            }

            val deleteButton = makeButton(
                "Delete",
                Color.rgb(145, 38, 38),
                white
            ) {
                AlertDialog.Builder(this)
                    .setTitle("سڕینەوەی سەرچاوە")
                    .setMessage("دڵنیایت دەتەوێت «${source.name}» بسڕیتەوە؟")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        if (source.type == "file") {
                            File(filesDir, source.value).delete()
                        }

                        sources.removeAll { it.id == source.id }
                        persistSources()
                        renderSources()
                        showStatus("سەرچاوەکە سڕایەوە.")
                    }
                    .show()
            }

            row.addView(
                useButton,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )

            row.addView(
                editButton,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )

            row.addView(
                deleteButton,
                LinearLayout.LayoutParams(0, dp(42), 1f)
            )

            box.addView(row)

            sourceList.addView(
                box,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(5), 0, dp(5))
                }
            )
        }
    }

    private fun editSource(source: PlaylistSource) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }

        val name = makeInput("ناوی سەرچاوە")
        name.setText(source.name)
        fields.addView(name, fieldParams())

        val value = makeInput(
            if (source.type == "portal") "Portal URL"
            else if (source.type == "file") "ناوی فایل"
            else "Playlist URL"
        )
        value.setText(source.value)
        fields.addView(value, fieldParams())

        val extra = if (source.type == "portal") {
            makeInput("MAC Address").also {
                it.setText(source.extra)
                fields.addView(it, fieldParams())
            }
        } else null

        AlertDialog.Builder(this)
            .setTitle("Edit Source")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newName = name.text.toString().trim()
                val newValue = value.text.toString().trim()

                if (newName.isBlank() || newValue.isBlank()) {
                    showStatus("ناو و زانیارییەکە نابێت بەتاڵ بن.")
                    return@setPositiveButton
                }

                if (source.type == "url" && !isHttpUrl(newValue)) {
                    showStatus("URL ـەکە دروست نییە.")
                    return@setPositiveButton
                }

                if (source.type == "portal" && !isHttpUrl(newValue)) {
                    showStatus("Portal URL ـەکە دروست نییە.")
                    return@setPositiveButton
                }

                source.name = newName

                if (source.type == "file") {
                    showStatus("ناوی سەرچاوەکە نوێ کرایەوە.")
                } else {
                    source.value = newValue
                }

                if (extra != null) {
                    source.extra = extra.text.toString().trim()
                }

                persistSources()
                renderSources()
                showStatus("گۆڕانکارییەکان پاشەکەوت کران.")
            }
            .show()
    }

    // --------------------------------------------------
    // FILES
    // --------------------------------------------------

    private fun importPlaylistFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }

                if (text.isNullOrBlank()) {
                    showStatus("فایلەکە بەتاڵە یان نەخوێندرایەوە.")
                    return@launch
                }

                if (!text.contains("#EXTINF", ignoreCase = true)) {
                    showStatus("ناوەڕۆکی فایلەکە وەک Playlist ـی M3U نەناسرا.")
                    return@launch
                }

                val fileName = "playlist_${System.currentTimeMillis()}.m3u"

                withContext(Dispatchers.IO) {
                    File(filesDir, fileName).writeText(text)
                }

                saveSource(
                    name = "M3U File ${sources.size + 1}",
                    type = "file",
                    value = fileName
                )

                playlistText = text
                lastUrl = ""

                parsePlaylist(text)
                showStatus("فایلەکە زیاد و پاشەکەوت کرا.")

                if (showingPlaylistPage) {
                    renderSources()
                }
            } catch (e: Exception) {
                showStatus(
                    "هەڵە لە خوێندنەوەی فایل: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private fun loadSavedFile(source: PlaylistSource) {
        lifecycleScope.launch {
            try {
                val file = File(filesDir, source.value)

                if (!file.exists()) {
                    showStatus("فایلەکە نەدۆزرایەوە.")
                    return@launch
                }

                val text = withContext(Dispatchers.IO) {
                    file.readText()
                }

                playlistText = text
                lastUrl = ""

                parsePlaylist(text)

                showingPlaylistPage = false
                buildHome()
                showStatus("فایلی ${source.name} بار کرا.")
            } catch (e: Exception) {
                showStatus("نەتوانرا فایلەکە بخوێندرێتەوە.")
            }
        }
    }

    // --------------------------------------------------
    // LOAD URL
    // --------------------------------------------------

    private fun isHttpUrl(value: String): Boolean {
        return value.startsWith("http://", true) ||
            value.startsWith("https://", true)
    }

    private fun loadUrl(url: String) {
        if (!isHttpUrl(url)) {
            showStatus("URL ـێکی دروستی HTTP یان HTTPS بنووسە.")
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
                    connection.readTimeout = 25000
                    connection.instanceFollowRedirects = true

                    connection.setRequestProperty(
                        "User-Agent",
                        "KAS-TV/1.0"
                    )

                    try {
                        val code = connection.responseCode

                        if (code !in 200..299) {
                            throw Exception("HTTP $code")
                        }

                        connection.inputStream
                            .bufferedReader()
                            .use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }
                }

                if (!result.contains("#EXTINF", ignoreCase = true)) {
                    showStatus(
                        "وەڵامی سێرڤەر وەک Playlist ـی M3U نەناسرا."
                    )
                    return@launch
                }

                playlistText = result
                parsePlaylist(result)

                showingPlaylistPage = false
                buildHome()

                showStatus(
                    "Playlist بار کرا. ژمارەی بابەتەکان: ${items.size}"
                )
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"

                showStatus(
                    "نەتوانرا Playlist بار بکرێت: $error"
                )
            } finally {
                stopSpinner()
            }
        }
    }

    private fun refreshPlaylist() {
        if (spinner?.isRunning == true) return

        if (lastUrl.isNotBlank()) {
            loadUrl(lastUrl)
        } else if (playlistText.isNotBlank()) {
            parsePlaylist(playlistText)
            showStatus("Playlist نوێ کرایەوە.")
        } else {
            showStatus("بچۆ Playlist و سەرچاوەیەک زیاد بکە.")
        }
    }

    // --------------------------------------------------
    // M3U PARSER
    // --------------------------------------------------

    private fun parsePlaylist(text: String) {
        items.clear()

        var name = "Channel"
        var group = ""

        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val comma = line.lastIndexOf(',')

                    name = if (comma >= 0 && comma < line.lastIndex) {
                        line.substring(comma + 1).trim()
                    } else {
                        "Channel"
                    }

                    val tvgName = Regex(
                        """tvg-name\s*=\s*"([^"]*)"""",
                        RegexOption.IGNORE_CASE
                    ).find(line)?.groupValues?.getOrNull(1)

                    if (!tvgName.isNullOrBlank()) {
                        name = tvgName
                    }

                    group = Regex(
                        """group-title\s*=\s*"([^"]*)"""",
                        RegexOption.IGNORE_CASE
                    ).find(line)?.groupValues?.getOrNull(1) ?: ""
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    group = line.substringAfter(":").trim()
                }

                line.startsWith("http://", true) ||
                line.startsWith("https://", true) -> {
                    items.add(
                        TvItem(
                            name = name.ifBlank { "Channel" },
                            url = line,
                            group = group
                        )
                    )

                    name = "Channel"
                    group = ""
                }
            }
        }

        if (::content.isInitialized && !showingPlaylistPage) {
            renderContent()
        }

        showStatus("ژمارەی بابەتە بارکراوەکان: ${items.size}")
    }

    // --------------------------------------------------
    // TV CONTENT
    // --------------------------------------------------

    private fun renderContent() {
        content.removeAllViews()

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
                        LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        }
                    )

                    index++
                } else {
                    row.addView(
                        View(this@MainActivity),
                        LinearLayout.LayoutParams(0, dp(78), 1f)
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

    // --------------------------------------------------
    // SOURCE STORAGE
    // --------------------------------------------------

    private fun saveSource(
        name: String,
        type: String,
        value: String,
        extra: String = ""
    ) {
        val source = PlaylistSource(
            id = System.currentTimeMillis().toString(),
            name = name,
            type = type,
            value = value,
            extra = extra
        )

        sources.add(source)
        persistSources()
    }

    private fun loadSources() {
        sources.clear()

        try {
            val array = JSONArray(
                prefs.getString("sources", "[]") ?: "[]"
            )

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                sources.add(
                    PlaylistSource(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        type = obj.optString("type"),
                        value = obj.optString("value"),
                        extra = obj.optString("extra")
                    )
                )
            }
        } catch (_: Exception) {
            sources.clear()
        }
    }

    private fun persistSources() {
        val array = JSONArray()

        sources.forEach { source ->
            array.put(
                JSONObject().apply {
                    put("id", source.id)
                    put("name", source.name)
                    put("type", source.type)
                    put("value", source.value)
                    put("extra", source.extra)
                }
            )
        }

        prefs.edit()
            .putString("sources", array.toString())
            .apply()
    }

    // --------------------------------------------------
    // UI HELPERS
    // --------------------------------------------------

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
                setPadding(dp(4), dp(14), dp(4), dp(7))
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

    private fun addButton(
        parent: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        val button = makeButton(text, gold, Color.BLACK, action)

        parent.addView(
            button,
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                setMargins(dp(2), dp(5), dp(2), dp(5))
            }
        )
    }

    private fun makeButton(
        text: String,
        color: Int,
        textColor: Int,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(color, 10)
            isFocusable = true
            isClickable = true

            setOnClickListener { action() }
        }
    }

    private fun fieldParams() =
        LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(dp(2), dp(4), dp(2), dp(4))
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showStatus(message: String) {
        if (::status.isInitialized) {
            status.text = message
        }
    }

    private fun startSpinner() {
        if (!::refreshIcon.isInitialized) return

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

    override fun onBackPressed() {
        if (showingPlaylistPage) {
            buildHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopSpinner()
        super.onDestroy()
    }
}

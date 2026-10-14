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
import java.util.UUID

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

    private var selectedCategory = "Live TV"
    private var showingPlaylistPage = false
    private var showingCategoryPage = false

    private lateinit var root: LinearLayout
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

    // ==================================================
    // HOME PAGE
    // تەنها پەڕەی سەرەکی و بەشەکان پیشان دەدات.
    // هیچ لیستی کەناڵێک لێرە پیشان نادرێت.
    // ==================================================

    private fun buildHome() {
        showingPlaylistPage = false
        showingCategoryPage = false

        root = newRoot()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgColor)
            addView(root)
        }

        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            TextView(this).apply {
                text = "KAS TV"
                textSize = 32f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
                gravity = Gravity.CENTER_VERTICAL
            },
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

        addHeading(root, "📺  بەشەکانی KAS TV")

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val categories = listOf(
            "📡  Live TV" to "Live TV",
            "🎬  Movies" to "Movies",
            "📂  Series" to "Series",
            "📋  Playlist" to "Playlist"
        )

        categories.forEach { (label, category) ->

            val button = makeButton(
                label,
                cardColor,
                white
            ) {
                when (category) {
                    "Playlist" -> buildPlaylistPage()
                    else -> {
                        selectedCategory = category
                        buildCategoryPage(category)
                    }
                }
            }

            tabs.addView(
                button,
                LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                    setMargins(dp(4), dp(5), dp(4), dp(5))
                }
            )
        }

        root.addView(tabs)

        addHeading(root, "📂  بەشێک هەڵبژێرە")

        addHomeCategory(
            root,
            "📡  LIVE TV",
            "کەناڵەکانی تەلەفزیۆنی ڕاستەوخۆ"
        ) {
            selectedCategory = "Live TV"
            buildCategoryPage("Live TV")
        }

        addHomeCategory(
            root,
            "🎬  MOVIES",
            "فیلمەکان بە شێوەی ئۆتۆماتیکی جیا دەکرێنەوە"
        ) {
            selectedCategory = "Movies"
            buildCategoryPage("Movies")
        }

        addHomeCategory(
            root,
            "📂  SERIES",
            "زنجیرە دراماکان و ئەڵقەکان"
        ) {
            selectedCategory = "Series"
            buildCategoryPage("Series")
        }

        addHeading(root, "📊  زانیاریی Playlist")

        val summary = TextView(this).apply {
            textSize = 15f
            setTextColor(white)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(cardColor, 12)
            text = buildSummary()
        }

        root.addView(
            summary,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
        )

        val refreshButton = makeButton(
            "⟳  Refresh Playlist",
            gold,
            Color.BLACK
        ) {
            refreshPlaylist()
        }

        refreshIcon = refreshButton

        root.addView(
            refreshButton,
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(grey)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }

        root.addView(status)
    }

    private fun addHomeCategory(
        parent: LinearLayout,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(cardColor, 12)
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
        }

        box.addView(
            TextView(this).apply {
                text = title
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
            }
        )

        box.addView(
            TextView(this).apply {
                text = description
                textSize = 13f
                setTextColor(grey)
                setPadding(0, dp(5), 0, 0)
            }
        )

        parent.addView(
            box,
            LinearLayout.LayoutParams(-1, dp(78)).apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )
    }

    private fun buildSummary(): String {
        val live = items.count { categoryOf(it) == "Live TV" }
        val movies = items.count { categoryOf(it) == "Movies" }
        val series = items.count { categoryOf(it) == "Series" }

        return "سەرچاوەکان: ${sources.size}\n" +
            "📡 Live TV: $live\n" +
            "🎬 Movies: $movies\n" +
            "📂 Series: $series"
    }

    private fun newRoot(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
            setBackgroundColor(bgColor)
        }
    }

    // ==================================================
    // CATEGORY PAGE
    // هەر بەشێک پەڕەیەکی جیاوازە.
    // ==================================================

    private fun buildCategoryPage(category: String) {
        showingPlaylistPage = false
        showingCategoryPage = true
        selectedCategory = category

        root = newRoot()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgColor)
            addView(root)
        }

        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            makeButton("‹  Back", cardColor, white) {
                buildHome()
                showStatus("گەڕایتەوە بۆ پەڕەی سەرەکی.")
            },
            LinearLayout.LayoutParams(dp(105), dp(46))
        )

        val title = when (category) {
            "Movies" -> "🎬  MOVIES"
            "Series" -> "📂  SERIES"
            else -> "📡  LIVE TV"
        }

        header.addView(
            TextView(this).apply {
                text = title
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, dp(50), 1f)
        )

        root.addView(header)

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(grey)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        root.addView(status)

        val filtered = items.filter {
            categoryOf(it) == category
        }

        addLabel(
            root,
            "ژمارەی بابەتەکان: ${filtered.size}"
        )

        if (filtered.isEmpty()) {
            addLabel(
                root,
                when {
                    items.isEmpty() ->
                        "هیچ Playlist ـێک بار نەکراوە.\nبچۆ Playlist، سەرچاوەیەک زیاد بکە و Open بکە."

                    category == "Live TV" ->
                        "هیچ کەناڵێکی Live TV نەدۆزرایەوە."

                    category == "Movies" ->
                        "هیچ فیلمێک نەدۆزرایەوە. گرووپی فیلمەکان لە M3U پشکنە."

                    else ->
                        "هیچ زنجیرەیەک نەدۆزرایەوە. گرووپی زنجیرەکان لە M3U پشکنە."
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

            root.addView(row)
        }
    }

    // ==================================================
    // AUTOMATIC CATEGORY DETECTION
    // ==================================================

    private fun categoryOf(item: TvItem): String {
        val group = item.group.lowercase()
        val name = item.name.lowercase()
        val url = item.url.lowercase()

        val combined = "$group $name $url"

        val movieWords = listOf(
            "movie", "movies", "film", "films",
            "cinema", "vod", "video on demand",
            "فیلم", "افلام", "أفلام"
        )

        val seriesWords = listOf(
            "series", "serial", "drama", "tv show",
            "shows", "season", "episode", "episodes",
            "مسلسل", "مسلسلات", "دراما", "زنجیرە",
            "ئەڵقە", "ئەڵقەکان"
        )

        if (movieWords.any { combined.contains(it) }) {
            return "Movies"
        }

        if (seriesWords.any { combined.contains(it) }) {
            return "Series"
        }

        // ئەگەر فیلم یان زنجیرە نەبوو،
        // بە شێوەی بنەڕەتی وەک Live TV دەناسرێت.
        return "Live TV"
    }

    // ==================================================
    // PLAYLIST MANAGER PAGE
    // ==================================================

    private fun buildPlaylistPage() {
        showingPlaylistPage = true
        showingCategoryPage = false

        root = newRoot()

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

        header.addView(
            makeButton("‹  Back", cardColor, white) {
                buildHome()
                showStatus("گەڕایتەوە بۆ KAS TV.")
            },
            LinearLayout.LayoutParams(dp(100), dp(48))
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

        // M3U URL

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
                name.ifBlank { "Playlist ${sources.size + 1}" },
                "url",
                url
            )

            nameInput.text.clear()
            urlInput.text.clear()

            renderSources()
            showStatus("Playlist زیاد و پاشەکەوت کرا. بۆ بارکردن Open بکە.")
        }

        // File import

        addHeading(root, "📁  M3U Playlist Files")

        addButton(root, "📂  Choose M3U File") {
            pickM3u.launch(
                arrayOf(
                    "audio/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "application/x-mpegURL",
                    "text/plain",
                    "*/*"
                )
            )
        }

        // Xtream Codes

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

        addButton(root, "＋  Save Xtream Playlist") {
            val base = server.text.toString().trim().trimEnd('/')
            val user = username.text.toString().trim()
            val pass = password.text.toString()

            if (!isHttpUrl(base) || user.isBlank() || pass.isBlank()) {
                showStatus("Server URL و Username و Password پڕ بکەرەوە.")
                return@addButton
            }

            val url =
                "$base/get.php?username=${Uri.encode(user)}" +
                    "&password=${Uri.encode(pass)}" +
                    "&type=m3u_plus&output=ts"

            saveSource(
                xtreamName.text.toString().trim()
                    .ifBlank { "Xtream - $user" },
                "url",
                url
            )

            xtreamName.text.clear()
            server.text.clear()
            username.text.clear()
            password.text.clear()

            renderSources()
            showStatus("Xtream پاشەکەوت کرا. لە لیستی خوارەوە Open بکە.")
        }

        // MAC / Portal

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
                name.ifBlank { "Portal - $mac" },
                "portal",
                portal,
                mac
            )

            portalName.text.clear()
            portalUrl.text.clear()
            macInput.text.clear()

            renderSources()

            showStatus(
                "Portal و MAC پاشەکەوت کران. پەخشکردنی Stalker پێویستی بە جێبەجێکردنی API ـی تایبەتی هەیە."
            )
        }

        addHeading(root, "📚  Saved Playlists & Sources")

        sourceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(sourceList)

        renderSources()
    }

    // ==================================================
    // SAVED SOURCE LIST
    // ==================================================

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

            val openLabel =
                if (source.type == "portal") "Portal Info" else "Open"

            row.addView(
                makeButton(openLabel, gold, Color.BLACK) {
                    when (source.type) {
                        "url" -> loadUrl(source.value)
                        "file" -> loadSavedFile(source)
                        "portal" -> showStatus(
                            "MAC: ${source.extra}. تۆمار کراوە، بەڵام Stalker API هێشتا جێبەجێ نەکراوە."
                        )
                    }
                },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )

            row.addView(
                makeButton("Edit", Color.rgb(60, 70, 85), white) {
                    editSource(source)
                },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )

            row.addView(
                makeButton("Delete", Color.rgb(145, 38, 38), white) {
                    AlertDialog.Builder(this)
                        .setTitle("سڕینەوەی سەرچاوە")
                        .setMessage(
                            "دڵنیایت دەتەوێت «${source.name}» بسڕیتەوە؟"
                        )
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
                },
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
        } else {
            null
        }

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

                if (
                    source.type != "file" &&
                    !isHttpUrl(newValue)
                ) {
                    showStatus("URL ـەکە دروست نییە.")
                    return@setPositiveButton
                }

                source.name = newName

                if (source.type != "file") {
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

    // ==================================================
    // IMPORT M3U FILE
    // ==================================================

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
                    showStatus(
                        "ناوەڕۆکی فایلەکە وەک Playlist ـی M3U نەناسرا."
                    )
                    return@launch
                }

                val fileName =
                    "playlist_${UUID.randomUUID()}.m3u"

                withContext(Dispatchers.IO) {
                    File(filesDir, fileName).writeText(text)
                }

                saveSource(
                    name = "M3U File ${sources.size + 1}",
                    type = "file",
                    value = fileName
                )

                renderSources()
                showStatus(
                    "فایلەکە زیاد و پاشەکەوت کرا. بۆ بارکردن Open بکە."
                )
            } catch (e: Exception) {
                showStatus(
                    "هەڵە لە خوێندنەوەی فایل: " +
                        (e.localizedMessage ?: "Unknown error")
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

                if (!text.contains("#EXTINF", ignoreCase = true)) {
                    showStatus("ناوەڕۆکی فایلەکە Playlist ـی M3U نییە.")
                    return@launch
                }

                playlistText = text
                lastUrl = ""

                parsePlaylist(text)

                buildHome()
                showStatus(
                    "فایلی ${source.name} بار کرا. ئێستا بەشەکەت هەڵبژێرە."
                )
            } catch (e: Exception) {
                showStatus(
                    "نەتوانرا فایلەکە بخوێندرێتەوە: " +
                        (e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    // ==================================================
    // LOAD PLAYLIST URL
    // ==================================================

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
                        "وەڵامی سێرڤەر وەک Playlist ـی M3U نەناسرا. " +
                            "دڵنیابە لەوەی لینکەکە لینکی Playlist ـە."
                    )
                    return@launch
                }

                playlistText = result
                parsePlaylist(result)

                buildHome()

                showStatus(
                    "Playlist بار کرا. ژمارەی بابەتەکان: ${items.size}. " +
                        "ئێستا Live TV یان Movies یان Series هەڵبژێرە."
                )
            } catch (e: Exception) {
                showStatus(
                    "نەتوانرا Playlist بار بکرێت: " +
                        (e.message ?: "Unknown error")
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
            buildHome()
            showStatus("Playlist نوێ کرایەوە.")
        } else {
            showStatus("بچۆ Playlist و سەرچاوەیەک زیاد بکە.")
        }
    }

    // ==================================================
    // M3U PARSER
    // ==================================================

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
    }

    // ==================================================
    // CHANNEL CARD / PLAYER
    // ==================================================

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

    // ==================================================
    // SOURCE STORAGE
    // ==================================================

    private fun saveSource(
        name: String,
        type: String,
        value: String,
        extra: String = ""
    ) {
        val source = PlaylistSource(
            id = UUID.randomUUID().toString(),
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

    // ==================================================
    // UI HELPERS
    // ==================================================

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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingPlaylistPage || showingCategoryPage) {
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

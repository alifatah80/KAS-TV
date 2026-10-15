package com.kas.tv

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

private data class TvItem(val name: String, val url: String, val group: String = "")
private data class PlaylistSource(
    val id: String,
    var name: String,
    var type: String,
    var value: String,
    var extra: String = ""
)

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<TvItem>()
    private val sources = mutableListOf<PlaylistSource>()
    private val prefs by lazy { getSharedPreferences("kas_tv_sources", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private val bg = Color.rgb(12, 13, 20)
    private val card = Color.rgb(29, 31, 43)
    private val white = Color.WHITE
    private val grey = Color.rgb(185, 190, 205)
    private val gold = Color.rgb(255, 204, 0)

    private var selectedCategory = "Home"
    private var activeSourceId = ""
    private var activeSourceName = ""
    private var lastUrl = ""
    private var playlistText = ""
    private var loading = false

    private lateinit var root: LinearLayout
    private lateinit var sourceList: LinearLayout

    private val pickPlaylist = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importPlaylistFile(uri)
    }

    private val autoRefreshTask = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("auto_refresh", false) && lastUrl.isNotBlank()) {
                val source = sources.firstOrNull { it.id == activeSourceId }
                loadUrl(lastUrl, source, quiet = true, returnHome = false)
            }
            handler.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadSources()
        buildHome()

        if (prefs.getBoolean("auto_load", true)) {
            val savedId = prefs.getString("last_source_id", null)
            val saved = sources.firstOrNull { it.id == savedId }
            if (saved != null) openSource(saved, returnHome = false, quiet = true)
        }
        handler.postDelayed(autoRefreshTask, 15 * 60 * 1000L)
    }

    // HOME: five circular menu buttons only.
    private fun buildHome() {
        selectedCategory = "Home"
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = "KAS TV"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(58)))

        root.addView(TextView(this).apply {
            text = "هەڵبژاردنی بەش"
            textSize = 14f
            setTextColor(grey)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(34)))

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(circleMenu("Live TV", "▶", Color.rgb(142, 68, 173)) { buildCategoryPage("Live TV") }, circleParams())
        row1.addView(circleMenu("Movies", "🎬", Color.rgb(238, 116, 164)) { buildCategoryPage("Movies") }, circleParams())
        grid.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(circleMenu("Series", "▣", Color.rgb(242, 132, 35)) { buildCategoryPage("Series") }, circleParams())
        row2.addView(circleMenu("Playlist", "☷", Color.rgb(222, 174, 0)) { buildPlaylistPage() }, circleParams())
        grid.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row3.addView(circleMenu("Setting", "⚙", Color.rgb(205, 48, 57)) { buildSettingsPage() }, circleParams())
        grid.addView(row3)

        root.addView(grid, LinearLayout.LayoutParams(-1, -2).apply {
            weight = 1f
            gravity = Gravity.CENTER
        })
        setContentView(root)
    }

    private fun circleParams() = LinearLayout.LayoutParams(dp(142), dp(142)).apply {
        setMargins(dp(9), dp(9), dp(9), dp(9))
    }

    private fun circleMenu(title: String, icon: String, color: Int, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(2), Color.argb(100, 255, 255, 255))
            }
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = title
            setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 30f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1, dp(46)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1, dp(32)))
        }
    }

    private fun buildCategoryPage(category: String) {
        selectedCategory = category
        root = newPageRoot()

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "KAS TV"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⌂  Home") { buildHome() }, LinearLayout.LayoutParams(dp(100), dp(42)))
        root.addView(header)

        val welcome = TextView(this).apply {
            text = if (activeSourceName.isBlank()) "بەخێربێیت بۆ $category" else "بەخێربێیت بۆ $activeSourceName\n$category"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        root.addView(welcome)

        val chosen = items.filter { categoryOf(it) == category }
        root.addView(TextView(this).apply {
            text = "${chosen.size} ${if (category == "Live TV") "کەناڵ" else "بڕگە"}"
            textSize = 13f
            setTextColor(grey)
            setPadding(0, 0, 0, dp(8))
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (activeSourceId.isBlank() || items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "هیچ Playlist ـێک بار نەکراوە.\nبچۆ بۆ Playlist و لینک یان فایل زیاد بکە."
                textSize = 16f
                setTextColor(grey)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(35), dp(18), dp(35))
            })
            list.addView(button("＋ زیادکردنی Playlist") { buildPlaylistPage() }, LinearLayout.LayoutParams(-1, dp(48)))
        } else if (chosen.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "لە Playlist ـی $activeSourceName بڕگەیەک بۆ $category نەدۆزرایەوە.\nتکایە group-title ـەکانی Playlist بپشکنە."
                textSize = 15f
                setTextColor(grey)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(35), dp(18), dp(35))
            })
        } else {
            chosen.forEachIndexed { index, item ->
                val itemRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = rounded(card, 12)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openPlayer(item, chosen, index) }
                }
                itemRow.addView(TextView(this).apply {
                    text = if (category == "Live TV") "📺" else "▶"
                    textSize = 22f
                    setPadding(0, 0, dp(12), 0)
                })
                itemRow.addView(TextView(this).apply {
                    text = item.name
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(white)
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, -2, 1f))
                itemRow.addView(TextView(this).apply { text = "▶"; textSize = 18f; setTextColor(gold) })
                list.addView(itemRow, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(4))
                })
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("＋ Playlist") { buildPlaylistPage() }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(0, dp(8), 0, 0)
        })
        setContentView(root)
    }

    private fun buildPlaylistPage() {
        selectedCategory = "Playlist"
        root = newPageRoot()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Playlist"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⌂ Home") { buildHome() }, LinearLayout.LayoutParams(dp(100), dp(42)))
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "لینک یان فایل زیاد بکە؛ کاتێک سەرکەوتوو بوو، دەگەڕێیتەوە سەرەتا."
            textSize = 13f
            setTextColor(grey)
            setPadding(0, dp(8), 0, dp(10))
        })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val urlInput = edit("لینکی M3U / M3U8")
        content.addView(urlInput)
        content.addView(button("بارکردنی URL") {
            val value = urlInput.text.toString().trim()
            if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                val source = PlaylistSource(UUID.randomUUID().toString(), "Playlist ${sources.size + 1}", "url", value)
                sources.add(source)
                saveSources()
                openSource(source, returnHome = true)
            } else toast("تکایە لینکێکی http یان https بنووسە")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(5), 0, dp(5)) })

        content.addView(button("هەڵبژاردنی فایلی M3U / M3U8") {
            pickPlaylist.launch(arrayOf("text/*", "application/octet-stream", "application/x-mpegURL", "application/vnd.apple.mpegurl", "*/*"))
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(12)) })

        content.addView(label("Xtream Codes"))
        val server = edit("Server URL، وەک http://example.com:8080")
        val username = edit("Username")
        val password = edit("Password")
        password.inputType = 129
        content.addView(server)
        content.addView(username)
        content.addView(password)
        content.addView(button("دروستکردن و بارکردنی Xtream") {
            val base = server.text.toString().trim().trimEnd('/')
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (base.isBlank() || user.isBlank() || pass.isBlank()) {
                toast("هەموو خانەکانی Xtream پڕ بکەرەوە")
            } else if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
                toast("Server URL دەبێت بە http:// یان https:// دەست پێ بکات")
            } else {
                val link = "$base/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=ts"
                val source = PlaylistSource(UUID.randomUUID().toString(), "Xtream - $user", "url", link)
                sources.add(source)
                saveSources()
                openSource(source, returnHome = true)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(5), 0, dp(12)) })

        content.addView(label("Playlist ـە پاشەکەوتکراوەکان"))
        sourceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sourceList)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderSources()
    }

    private fun renderSources() {
        if (!::sourceList.isInitialized) return
        sourceList.removeAllViews()
        if (sources.isEmpty()) sourceList.addView(label("هێشتا هیچ Playlist ـێک زیاد نەکراوە."))
        sources.toList().forEach { source ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(card, 10)
            }
            row.addView(TextView(this).apply {
                text = source.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            })
            row.addView(TextView(this).apply {
                text = source.type.uppercase()
                textSize = 11f
                setTextColor(grey)
                maxLines = 1
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(button("کردنەوە") { openSource(source, returnHome = true) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, dp(6), dp(4), 0) })
            buttons.addView(button("ناونان") { renameSource(source) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), dp(6), dp(4), 0) })
            buttons.addView(button("سڕینەوە") {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("ئەم Playlist ـە بسڕدرێتەوە؟")
                    .setNegativeButton("نەخێر", null)
                    .setPositiveButton("بەڵێ") { _, _ ->
                        sources.remove(source)
                        if (activeSourceId == source.id) {
                            activeSourceId = ""
                            activeSourceName = ""
                            items.clear()
                        }
                        saveSources()
                        renderSources()
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), dp(6), 0, 0) })
            row.addView(buttons)
            sourceList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
    }

    private fun renameSource(source: PlaylistSource) {
        val input = edit("ناوی Playlist", source.name)
        AlertDialog.Builder(this)
            .setTitle("گۆڕینی ناوی Playlist")
            .setView(input)
            .setNegativeButton("پاشگەزبوونەوە", null)
            .setPositiveButton("پاشەکەوت") { _, _ ->
                source.name = input.text.toString().trim().ifBlank { source.name }
                if (activeSourceId == source.id) activeSourceName = source.name
                saveSources()
                renderSources()
            }.show()
    }

    private fun openSource(source: PlaylistSource, returnHome: Boolean = true, quiet: Boolean = false) {
        prefs.edit().putString("last_source_id", source.id).apply()
        when (source.type) {
            "url" -> loadUrl(source.value, source, quiet, returnHome)
            "file" -> lifecycleScope.launch {
                try {
                    val file = File(source.value)
                    if (!file.exists()) throw Exception("فایلەکە نەدۆزرایەوە")
                    val text = withContext(Dispatchers.IO) { file.readText() }
                    activatePlaylist(text, source, returnHome)
                } catch (e: Exception) {
                    toast("نەتوانرا Playlist بکرێتەوە: ${e.localizedMessage ?: "هەڵە"}")
                }
            }
            "portal" -> toast("Stalker Portal لەم وەشانەدا پەخشکردنی ڕاستەوخۆی API ـی تێدا جێبەجێ نەکراوە.")
        }
    }

    private fun importPlaylistFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (text.isBlank() || !text.contains("#EXTINF", true)) {
                    toast("فایلەکە بەتاڵە یان Playlist ـی M3U نییە")
                    return@launch
                }
                val file = File(filesDir, "playlist_${UUID.randomUUID()}.m3u")
                withContext(Dispatchers.IO) { file.writeText(text) }
                val source = PlaylistSource(UUID.randomUUID().toString(), "M3U ${sources.size + 1}", "file", file.absolutePath)
                sources.add(source)
                saveSources()
                activatePlaylist(text, source, returnHome = true)
                toast("Playlist زیادکرا و پاشەکەوت کرا")
            } catch (e: Exception) {
                toast("نەتوانرا فایلەکە بخوێندرێتەوە: ${e.localizedMessage ?: "هەڵە"}")
            }
        }
    }

    private fun loadUrl(url: String, source: PlaylistSource?, quiet: Boolean = false, returnHome: Boolean = true) {
        if (loading) {
            if (!quiet) toast("تکایە چاوەڕێ بکە")
            return
        }
        loading = true
        if (!quiet) toast("Playlist بار دەکرێت...")
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 25000
                        setRequestProperty("User-Agent", "KAS-TV Android")
                        instanceFollowRedirects = true
                    }
                    try {
                        val code = connection.responseCode
                        if (code !in 200..299) throw Exception("HTTP $code")
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }
                }
                if (!text.contains("#EXTINF", true)) throw Exception("ئەم لینکە Playlist ـی M3U نییە")
                val active = source ?: sources.firstOrNull { it.value == url }
                if (active == null) {
                    val newSource = PlaylistSource(UUID.randomUUID().toString(), "Playlist ${sources.size + 1}", "url", url)
                    sources.add(newSource)
                    saveSources()
                    activatePlaylist(text, newSource, returnHome)
                } else {
                    activatePlaylist(text, active, returnHome)
                }
                lastUrl = url
                prefs.edit().putString("last_url", url).apply()
                if (!quiet) toast("${items.size} بڕگە بارکرا")
            } catch (e: Exception) {
                if (!quiet) toast("بارکردن سەرکەوتوو نەبوو: ${e.localizedMessage ?: "هەڵەی نەناسراو"}")
            } finally {
                loading = false
            }
        }
    }

    private fun activatePlaylist(text: String, source: PlaylistSource, returnHome: Boolean) {
        val parsed = parsePlaylist(text)
        if (parsed.isEmpty()) {
            toast("هیچ لینکێکی ڤیدیۆ لە Playlist ـەکەدا نەدۆزرایەوە")
            return
        }
        playlistText = text
        items.clear()
        items.addAll(parsed)
        activeSourceId = source.id
        activeSourceName = source.name
        if (source.type == "url") {
            lastUrl = source.value
            prefs.edit().putString("last_url", source.value).apply()
        }
        prefs.edit().putString("last_source_id", source.id).apply()
        if (returnHome) buildHome()
    }

    private fun parsePlaylist(text: String): List<TvItem> {
        val result = mutableListOf<TvItem>()
        var name = ""
        var group = ""
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (line in lines) {
            if (line.startsWith("#EXTINF", true)) {
                val title = line.substringAfterLast(',').trim()
                name = Regex("tvg-name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: title.ifBlank { "کەناڵ" }
                group = Regex("group-title\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1) ?: ""
            } else if (!line.startsWith("#") && (line.startsWith("http://", true) || line.startsWith("https://", true))) {
                result.add(TvItem(name.ifBlank { "Channel ${result.size + 1}" }, line, group))
                name = ""
                group = ""
            }
        }
        return result
    }

    private fun categoryOf(item: TvItem): String {
        val s = "${item.group} ${item.name} ${item.url}".lowercase()
        return when {
            listOf("series", "tv show", "season", "episode", "serie", "مسلسل", "زنجیرە").any { s.contains(it) } -> "Series"
            listOf("movie", "movies", "vod", "film", "cinema", "فیلم", "ئەفلام").any { s.contains(it) } -> "Movies"
            else -> "Live TV"
        }
    }

    private fun openPlayer(item: TvItem, channels: List<TvItem>, index: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", item.url)
            putExtra("name", item.name)
            putExtra("selected_index", index)
            putStringArrayListExtra("channel_names", ArrayList(channels.map { it.name }))
            putStringArrayListExtra("channel_urls", ArrayList(channels.map { it.url }))
            putExtra("player_fullscreen", prefs.getBoolean("player_fullscreen", true))
        }
        startActivity(intent)
    }

    private fun buildSettingsPage() {
        selectedCategory = "Setting"
        root = newPageRoot()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Setting"; textSize = 23f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⌂ Home") { buildHome() }, LinearLayout.LayoutParams(dp(100), dp(42)))
        root.addView(header)

        val autoLoad = CheckBox(this).apply { text = "بارکردنی خۆکاری دوا Playlist"; setTextColor(white); isChecked = prefs.getBoolean("auto_load", true) }
        val autoRefresh = CheckBox(this).apply { text = "نوێکردنەوەی خۆکار هەر ١٥ خولەک"; setTextColor(white); isChecked = prefs.getBoolean("auto_refresh", false) }
        val fullscreen = CheckBox(this).apply { text = "پەخشکردن بە شێوەی فول‌سکرین"; setTextColor(white); isChecked = prefs.getBoolean("player_fullscreen", true) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(18), dp(8), dp(18))
            addView(autoLoad)
            addView(autoRefresh)
            addView(fullscreen)
        }
        root.addView(box)
        root.addView(button("پاشەکەوتکردنی ڕێکخستنەکان") {
            prefs.edit()
                .putBoolean("auto_load", autoLoad.isChecked)
                .putBoolean("auto_refresh", autoRefresh.isChecked)
                .putBoolean("player_fullscreen", fullscreen.isChecked)
                .apply()
            toast("ڕێکخستنەکان پاشەکەوت کران")
            buildHome()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(button("پاککردنەوەی Cache") {
            try {
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                toast("Cache پاککرایەوە")
            } catch (_: Exception) {
                toast("نەتوانرا Cache پاک بکرێتەوە")
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(8), 0, 0) })
        setContentView(root)
    }

    private fun newPageRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(bg)
    }

    private fun loadSources() {
        sources.clear()
        val array = try { JSONArray(prefs.getString("sources", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            sources.add(
                PlaylistSource(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("name", "Playlist"),
                    o.optString("type", "url"),
                    o.optString("value", ""),
                    o.optString("extra", "")
                )
            )
        }
        lastUrl = prefs.getString("last_url", "") ?: ""
    }

    private fun saveSources() {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("name", source.name)
                    .put("type", source.type)
                    .put("value", source.value)
                    .put("extra", source.extra)
            )
        }
        prefs.edit().putString("sources", array.toString()).apply()
    }

    private fun edit(hintText: String, value: String = "") = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 14f
        setTextColor(white)
        setHintTextColor(grey)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = rounded(card, 8)
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(gold)
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun button(value: String, action: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = rounded(card, 9)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (selectedCategory == "Home") super.onBackPressed() else buildHome()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoRefreshTask)
        super.onDestroy()
    }
}

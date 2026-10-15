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
private data class PlaylistSource(val id: String, var name: String, var type: String, var value: String, var extra: String = "")

class MainActivity : AppCompatActivity() {
    private val items = mutableListOf<TvItem>()
    private val sources = mutableListOf<PlaylistSource>()
    private val prefs by lazy { getSharedPreferences("kas_tv_sources", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val gold = Color.rgb(245, 180, 0)
    private val bg = Color.rgb(12, 13, 17)
    private val card = Color.rgb(29, 31, 39)
    private val white = Color.WHITE
    private val grey = Color.rgb(175, 179, 190)
    private var selectedCategory = "Live TV"
    private var lastUrl = ""
    private var playlistText = ""
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var sourceList: LinearLayout
    private var loading = false

    private val pickPlaylist = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importPlaylistFile(uri)
    }

    private val autoRefreshTask = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("auto_refresh", false) && lastUrl.isNotBlank()) {
                loadUrl(lastUrl, quiet = true)
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
        status.text = if (items.isEmpty()) "سەرچاوەی لیستی کەناڵ زیاد بکە" else "${items.size} کەناڵ بارکراوە"
        if (prefs.getBoolean("auto_load", true)) {
            val savedId = prefs.getString("last_source_id", null)
            val saved = sources.firstOrNull { it.id == savedId }
            if (saved != null) openSource(saved)
        }
        handler.postDelayed(autoRefreshTask, 15 * 60 * 1000L)
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "KAS TV"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⚙️") { showSettings() }, LinearLayout.LayoutParams(dp(54), dp(46)))
        root.addView(header)
        val subtitle = TextView(this).apply { text = "Live TV • Movies • Series"; textSize = 13f; setTextColor(grey); setPadding(0, dp(3), 0, dp(12)) }
        root.addView(subtitle)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Live TV", "Movies", "Series", "Playlist").forEach { category ->
            nav.addView(button(category) {
                selectedCategory = category
                if (category == "Playlist") buildPlaylistPage() else buildCategoryPage(category)
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(nav)
        status = TextView(this).apply {
            textSize = 13f; setTextColor(grey); setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("＋ زیادکردنی لیست") { buildPlaylistPage() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(5), 0) })
        actions.addView(button("↻ نوێکردنەوە") { refreshPlaylist() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(5), 0, 0, 0) })
        root.addView(actions)
        setContentView(root)
        buildCategoryPage("Live TV")
    }

    private fun buildCategoryPage(category: String) {
        selectedCategory = category
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "KAS TV"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⚙️") { showSettings() }, LinearLayout.LayoutParams(dp(50), dp(44)))
        root.addView(header)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Live TV", "Movies", "Series", "Playlist").forEach { c ->
            nav.addView(button(c) { if (c == "Playlist") buildPlaylistPage() else buildCategoryPage(c) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(4)) })
        }
        root.addView(nav)
        status = TextView(this).apply { text = "${items.size} کەناڵ"; textSize = 13f; setTextColor(grey); setPadding(dp(2), dp(8), dp(2), dp(8)) }
        root.addView(status)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val filtered = items.filter { categoryOf(it) == category }
        if (filtered.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (items.isEmpty()) "هێشتا لیستی M3U زیاد نەکراوە. لە Playlist سەرچاوە زیاد بکە." else "لە بەشی $category کەناڵ نەدۆزرایەوە."
                textSize = 16f; setTextColor(grey); gravity = Gravity.CENTER; setPadding(dp(18), dp(35), dp(18), dp(35))
            })
        } else {
            filtered.forEachIndexed { index, item ->
                val channelCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(card, 10)
                    isClickable = true; isFocusable = true
                    setOnClickListener { openPlayer(item, filtered, index) }
                }
                channelCard.addView(TextView(this).apply { text = "📺"; textSize = 20f; setPadding(0, 0, dp(10), 0) })
                channelCard.addView(TextView(this).apply { text = item.name; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white); maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
                channelCard.addView(TextView(this).apply { text = "▶"; textSize = 18f; setTextColor(gold) })
                list.addView(channelCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(button("＋ Playlist") { buildPlaylistPage() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, dp(6), dp(5), 0) })
        footer.addView(button("↻ Refresh") { refreshPlaylist() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(5), dp(6), 0, 0) })
        root.addView(footer)
    }

    private fun buildPlaylistPage() {
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Playlist / سەرچاوەکان"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⌂") { buildCategoryPage("Live TV") }, LinearLayout.LayoutParams(dp(50), dp(44)))
        root.addView(header)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        val urlInput = edit("لینکی M3U / M3U8")
        content.addView(urlInput)
        content.addView(button("بارکردنی URL") {
            val value = urlInput.text.toString().trim()
            if (value.startsWith("http://") || value.startsWith("https://")) {
                val source = PlaylistSource(UUID.randomUUID().toString(), "Playlist ${sources.size + 1}", "url", value)
                sources.add(source); saveSources(); openSource(source)
            } else toast("تکایە لینکێکی دروست بنووسە")
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(5), 0, dp(5)) })
        content.addView(button("هەڵبژاردنی فایلی M3U") { pickPlaylist.launch(arrayOf("text/*", "application/octet-stream", "application/x-mpegURL", "application/vnd.apple.mpegurl")) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(4), 0, dp(12)) })
        content.addView(label("Xtream Codes"))
        val server = edit("Server URL، بۆ نموونە http://example.com:8080")
        val username = edit("Username")
        val password = edit("Password")
        content.addView(server); content.addView(username); content.addView(password)
        content.addView(button("دروستکردن و بارکردنی Xtream") {
            val base = server.text.toString().trim().trimEnd('/')
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (base.isBlank() || user.isBlank() || pass.isBlank()) toast("هەموو خانەکانی Xtream پڕ بکەرەوە")
            else {
                val link = "$base/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=ts"
                val source = PlaylistSource(UUID.randomUUID().toString(), "Xtream - $user", "url", link)
                sources.add(source); saveSources(); openSource(source)
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(5), 0, dp(12)) })
        content.addView(label("سەرچاوە پاشەکەوتکراوەکان"))
        sourceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sourceList)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderSources()
    }

    private fun renderSources() {
        if (!::sourceList.isInitialized) return
        sourceList.removeAllViews()
        if (sources.isEmpty()) sourceList.addView(label("هیچ سەرچاوەیەک پاشەکەوت نەکراوە."))
        sources.forEach { source ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(card, 8) }
            row.addView(TextView(this).apply { text = source.name; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white) })
            row.addView(TextView(this).apply { text = source.type.uppercase(); textSize = 11f; setTextColor(grey); maxLines = 1 })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(button("کردنەوە") { openSource(source) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, dp(5), dp(4), 0) })
            buttons.addView(button("ناونان") { renameSource(source) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), dp(5), dp(4), 0) })
            buttons.addView(button("سڕینەوە") { AlertDialog.Builder(this).setMessage("سەرچاوەکە بسڕدرێتەوە؟").setNegativeButton("نەخێر", null).setPositiveButton("بەڵێ") { _, _ -> sources.remove(source); saveSources(); renderSources() }.show() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), dp(5), 0, 0) })
            row.addView(buttons)
            sourceList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
    }

    private fun renameSource(source: PlaylistSource) {
        val input = edit("ناوی سەرچاوە", source.name)
        AlertDialog.Builder(this).setTitle("گۆڕینی ناو").setView(input)
            .setNegativeButton("پاشگەزبوونەوە", null)
            .setPositiveButton("پاشەکەوت") { _, _ -> source.name = input.text.toString().trim().ifBlank { source.name }; saveSources(); renderSources() }.show()
    }

    private fun openSource(source: PlaylistSource) {
        prefs.edit().putString("last_source_id", source.id).apply()
        when (source.type) {
            "url" -> loadUrl(source.value)
            "file" -> {
                val file = File(source.value)
                if (file.exists()) parseAndShow(file.readText(), "فایلی ${source.name}") else toast("فایلەکە نەدۆزرایەوە")
            }
            "portal" -> toast("Stalker Portal لەم وەشانەدا تەنها پاشەکەوت دەکرێت؛ API ـی پۆرتاڵ جێبەجێ نەکراوە.")
        }
    }

    private fun importPlaylistFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
                if (text.isBlank()) { toast("فایلەکە بەتاڵە"); return@launch }
                val file = File(filesDir, "playlist_${UUID.randomUUID()}.m3u")
                withContext(Dispatchers.IO) { file.writeText(text) }
                val source = PlaylistSource(UUID.randomUUID().toString(), "M3U ${sources.size + 1}", "file", file.absolutePath)
                sources.add(source); saveSources(); parseAndShow(text, source.name); buildPlaylistPage(); toast("Playlist زیادکرا")
            } catch (e: Exception) { toast("نەتوانرا فایلەکە بخوێندرێتەوە: ${e.localizedMessage}") }
        }
    }

    private fun loadUrl(url: String, quiet: Boolean = false) {
        if (loading) { if (!quiet) toast("تکایە چاوەڕێ بکە"); return }
        loading = true
        if (!quiet && ::status.isInitialized) status.text = "بارکردنی Playlist..."
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000; readTimeout = 25000
                        setRequestProperty("User-Agent", "KAS-TV Android")
                        instanceFollowRedirects = true
                    }
                    try {
                        val code = connection.responseCode
                        if (code !in 200..299) throw Exception("HTTP $code")
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } finally { connection.disconnect() }
                }
                if (!text.contains("#EXTINF", true)) throw Exception("ئەم لینکە Playlist ـی M3U نییە")
                lastUrl = url
                prefs.edit().putString("last_url", url).apply()
                parseAndShow(text, "Playlist")
                if (!quiet) toast("${items.size} کەناڵ بارکرا")
            } catch (e: Exception) {
                if (!quiet) { toast("بارکردن سەرکەوتوو نەبوو: ${e.localizedMessage ?: "هەڵەی نەناسراو"}"); if (::status.isInitialized) status.text = "بارکردن سەرکەوتوو نەبوو" }
            } finally { loading = false }
        }
    }

    private fun parseAndShow(text: String, sourceName: String) {
        playlistText = text
        val parsed = parsePlaylist(text)
        items.clear(); items.addAll(parsed)
        buildCategoryPage("Live TV")
        status.text = "$sourceName: ${items.size} کەناڵ"
    }

    private fun parsePlaylist(text: String): List<TvItem> {
        val result = mutableListOf<TvItem>()
        var name = ""
        var group = ""
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (line in lines) {
            if (line.startsWith("#EXTINF", true)) {
                val title = line.substringAfterLast(',').trim()
                name = Regex("tvg-name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: title.ifBlank { "کەناڵ" }
                group = Regex("group-title\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1) ?: ""
            } else if (!line.startsWith("#") && (line.startsWith("http://", true) || line.startsWith("https://", true))) {
                result.add(TvItem(name.ifBlank { "Channel ${result.size + 1}" }, line, group))
                name = ""; group = ""
            }
        }
        return result
    }

    private fun categoryOf(item: TvItem): String {
        val s = "${item.group} ${item.name} ${item.url}".lowercase()
        return when {
            listOf("series", "series", "tv show", "season", "episode", "مسلسل", "زنجیرە").any { s.contains(it) } -> "Series"
            listOf("movie", "movies", "vod", "film", "cinema", "فیلم", "ئەفلام").any { s.contains(it) } -> "Movies"
            else -> "Live TV"
        }
    }

    private fun openPlayer(item: TvItem, channels: List<TvItem>, index: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", item.url); putExtra("name", item.name); putExtra("selected_index", index)
            putStringArrayListExtra("channel_names", ArrayList(channels.map { it.name }))
            putStringArrayListExtra("channel_urls", ArrayList(channels.map { it.url }))
            putExtra("player_fullscreen", prefs.getBoolean("player_fullscreen", true))
        }
        startActivity(intent)
    }

    private fun refreshPlaylist() {
        val url = lastUrl.ifBlank { prefs.getString("last_url", "") ?: "" }
        if (url.isNotBlank()) loadUrl(url) else if (playlistText.isNotBlank()) parseAndShow(playlistText, "Playlist") else toast("سەرەتا Playlist زیاد بکە")
    }

    private fun showSettings() {
        val autoLoad = CheckBox(this).apply { text = "بارکردنی خۆکاری دوا سەرچاوە"; setTextColor(white); isChecked = prefs.getBoolean("auto_load", true) }
        val autoRefresh = CheckBox(this).apply { text = "نوێکردنەوەی خۆکار هەر ١٥ خولەک"; setTextColor(white); isChecked = prefs.getBoolean("auto_refresh", false) }
        val fullscreen = CheckBox(this).apply { text = "پلەیەر بە شێوەی پڕشاشە"; setTextColor(white); isChecked = prefs.getBoolean("player_fullscreen", true) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)); addView(autoLoad); addView(autoRefresh); addView(fullscreen) }
        AlertDialog.Builder(this).setTitle("ڕێکخستنەکانی KAS TV").setView(box)
            .setPositiveButton("پاشەکەوت") { _, _ ->
                prefs.edit().putBoolean("auto_load", autoLoad.isChecked).putBoolean("auto_refresh", autoRefresh.isChecked).putBoolean("player_fullscreen", fullscreen.isChecked).apply()
                toast("ڕێکخستنەکان پاشەکەوت کران")
            }.setNeutralButton("پاککردنەوەی Cache") { _, _ ->
                try { cacheDir.listFiles()?.forEach { it.deleteRecursively() }; toast("Cache پاککرایەوە") } catch (_: Exception) { toast("نەتوانرا Cache پاک بکرێتەوە") }
            }.setNegativeButton("داخستن", null).show()
    }

    private fun loadSources() {
        sources.clear()
        val array = try { JSONArray(prefs.getString("sources", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            sources.add(PlaylistSource(o.optString("id", UUID.randomUUID().toString()), o.optString("name", "Playlist"), o.optString("type", "url"), o.optString("value", ""), o.optString("extra", "")))
        }
        lastUrl = prefs.getString("last_url", "") ?: ""
    }

    private fun saveSources() {
        val array = JSONArray()
        sources.forEach { s -> array.put(JSONObject().put("id", s.id).put("name", s.name).put("type", s.type).put("value", s.value).put("extra", s.extra)) }
        prefs.edit().putString("sources", array.toString()).apply()
    }

    private fun edit(hintText: String, value: String = ""): EditText = EditText(this).apply {
        hint = hintText; setText(value); textSize = 14f; setTextColor(white); setHintTextColor(grey)
        setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(card, 8)
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) }
    }

    private fun label(textValue: String) = TextView(this).apply { text = textValue; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold); setPadding(0, dp(10), 0, dp(4)) }

    private fun button(textValue: String, action: () -> Unit) = TextView(this).apply {
        text = textValue; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (textValue.contains("⚙") || textValue == "⌂") gold else white)
        gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(6)); background = rounded(card, 8)
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onBackPressed() {
        if (selectedCategory == "Playlist") buildCategoryPage("Live TV") else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoRefreshTask)
        super.onDestroy()
    }
}

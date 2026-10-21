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
import android.text.InputType
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
    private var categoryAfterSourceLoad: String? = null
    private var pendingFileName: String = ""
    private var playlistStatusView: TextView? = null
    private var loadingDialog: AlertDialog? = null

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
        row1.addView(circleMenu("Live TV", "▶", Color.rgb(142, 68, 173)) { openCategory("Live TV") }, circleParams())
        row1.addView(circleMenu("Movies", "🎬", Color.rgb(238, 116, 164)) { openCategory("Movies") }, circleParams())
        grid.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(circleMenu("Series", "▣", Color.rgb(242, 132, 35)) { openCategory("Series") }, circleParams())
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

    private fun openCategory(category: String) {
        if (sources.isEmpty()) {
            buildCategoryPage(category)
            return
        }

        if (sources.size == 1) {
            val source = sources.first()
            if (source.id == activeSourceId && items.isNotEmpty()) {
                buildCategoryPage(category)
            } else {
                categoryAfterSourceLoad = category
                openSource(source, returnHome = false)
            }
            return
        }

        val sourceNames = sources.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Playlist هەڵبژێرە بۆ $category")
            .setItems(sourceNames) { _, which ->
                val source = sources.getOrNull(which) ?: return@setItems
                if (source.id == activeSourceId && items.isNotEmpty()) {
                    buildCategoryPage(category)
                } else {
                    categoryAfterSourceLoad = category
                    openSource(source, returnHome = false)
                }
            }
            .setNegativeButton("پاشگەزبوونەوە", null)
            .show()
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

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(label("١. زیادکردنی لینک یان سەرچاوە"))
        content.addView(TextView(this).apply {
            text = "ناوێک بنووسە، زانیارییەکان داخڵ بکە و Save بکە. ئەگەر لینکەکە دروست بوو، دەچێتە Live TV؛ ئەگەر سەرکەوتوو نەبوو، هەڵە بە بۆکسی سوور دەردەکەوێت."
            textSize = 13f; setTextColor(grey); setPadding(0, dp(2), 0, dp(8))
        })
        val sourceName = edit("ناوی Playlist بنووسە")
        content.addView(sourceName)
        val status = TextView(this).apply {
            textSize = 13f; setPadding(dp(10), dp(10), dp(10), dp(10)); visibility = View.GONE
        }
        playlistStatusView = status
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(6)) })

        content.addView(label("M3U / M3U8 URL"))
        val urlInput = edit("لینکی تەواوی http:// یان https://")
        content.addView(urlInput)
        content.addView(button("Save و تاقیکردنەوەی لینک") {
            val name = sourceName.text.toString().trim()
            val url = urlInput.text.toString().trim()
            if (name.isBlank()) showPlaylistStatus("تکایە ناوی Playlist بنووسە", false)
            else if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) showPlaylistStatus("تکایە لینکێکی دروستی http یان https بنووسە", false)
            else {
                val source = PlaylistSource(UUID.randomUUID().toString(), name, "url", url)
                loadUrl(url, source, quiet = false, returnHome = true)
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(4), 0, dp(8)) })

        content.addView(label("M3U / M3U8 فایل"))
        content.addView(button("هەڵبژاردنی فایل و Save") {
            val name = sourceName.text.toString().trim()
            if (name.isBlank()) showPlaylistStatus("پێش هەڵبژاردنی فایل، ناوی Playlist بنووسە", false)
            else {
                pendingFileName = name
                pickPlaylist.launch(arrayOf("text/*", "application/octet-stream", "application/x-mpegURL", "application/vnd.apple.mpegurl", "*/*"))
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(4), 0, dp(8)) })

        content.addView(label("Xtream Codes"))
        val xtreamName = edit("ناوی Playlist ـی Xtream")
        val server = edit("Server URL، وەک http://example.com:8080")
        val username = edit("Username")
        val password = edit("Password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        content.addView(xtreamName); content.addView(server); content.addView(username); content.addView(password)
        content.addView(button("Save و تاقیکردنەوەی Xtream") {
            val name = xtreamName.text.toString().trim()
            val base = server.text.toString().trim().trimEnd('/')
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (name.isBlank() || base.isBlank() || user.isBlank() || pass.isBlank()) showPlaylistStatus("ناو، Server URL، Username و Password پڕ بکەرەوە", false)
            else if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) showPlaylistStatus("Server URL دەبێت بە http:// یان https:// دەست پێ بکات", false)
            else {
                val link = "$base/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=ts"
                loadUrl(link, PlaylistSource(UUID.randomUUID().toString(), name, "url", link), quiet = false, returnHome = true)
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(4), 0, dp(8)) })

        content.addView(label("MAC / Stalker Portal"))
        val portalName = edit("ناوی Playlist ـی MAC")
        val portalUrl = edit("Portal URL، وەک http://server:8080/c/")
        val macAddress = edit("MAC Address، وەک 00:1A:79:XX:XX:XX")
        content.addView(portalName); content.addView(portalUrl); content.addView(macAddress)
        content.addView(button("پاشەکەوتکردنی MAC / Portal") {
            val name = portalName.text.toString().trim()
            val base = portalUrl.text.toString().trim()
            val mac = macAddress.text.toString().trim().uppercase()
            if (name.isBlank() || base.isBlank() || mac.isBlank()) showPlaylistStatus("ناو، Portal URL و MAC Address پڕ بکەرەوە", false)
            else if ((!base.startsWith("http://", true) && !base.startsWith("https://", true)) || !mac.matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))) showPlaylistStatus("Portal URL یان MAC Address دروست نییە", false)
            else {
                val existing = sources.firstOrNull { it.type == "portal" && it.value.equals(base, true) && it.extra.equals(mac, true) }
                if (existing != null) {
                    existing.name = name; saveSources(); renderSources()
                } else sources.add(PlaylistSource(UUID.randomUUID().toString(), name, "portal", base, mac))
                saveSources(); renderSources()
                showPlaylistStatus("MAC / Portal پاشەکەوت کرا. تێبینی: پەخشکردنی کەناڵەکانی Stalker پێویستی بە جێبەجێکردنی API ـی Portal هەیە و هێشتا لەم وەشانەدا چالاک نییە.", true)
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(4), 0, dp(12)) })

        content.addView(label("٢. Playlist ـە پاشەکەوتکراوەکان — کردنەوە، ئیدیت، سڕینەوە"))
        sourceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sourceList)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderSources()
    }

    private fun showLoadingDialog(message: String = "تکایە چاوەڕوان بە، Playlist پشکنین و بارکردن دەکرێت...") {
        if (isFinishing || isDestroyed) return
        dismissLoadingDialog()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(22), dp(24), dp(22))
        }
        panel.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(14)
        })
        panel.addView(TextView(this).apply {
            text = message
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(white)
        })
        loadingDialog = AlertDialog.Builder(this)
            .setView(panel)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showPlaylistSuccess(message: String = "سەرکەوتوو بوو! Playlist قبوڵ کرا.") {
        dismissLoadingDialog()
        if (isFinishing || isDestroyed) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("✅ سەرکەوتوو بوو")
            .setMessage(message)
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed && dialog.isShowing) dialog.dismiss()
            }, 1300L)
        }
        dialog.show()
    }

    private fun showPlaylistStatus(message: String, success: Boolean) {
        val view = playlistStatusView ?: return
        view.text = message
        view.setTextColor(if (success) Color.rgb(170, 255, 190) else Color.rgb(255, 255, 255))
        view.background = rounded(if (success) Color.rgb(20, 75, 42) else Color.rgb(170, 25, 35), 8)
        view.visibility = View.VISIBLE
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
                text = when (source.type) { "url" -> "M3U / M3U8 / XTREAM"; "file" -> "M3U FILE"; "portal" -> "MAC / STALKER PORTAL"; else -> source.type.uppercase() }
                textSize = 11f
                setTextColor(grey)
                maxLines = 1
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(button("کردنەوە") { openSource(source, returnHome = true) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, dp(6), dp(4), 0) })
            buttons.addView(button("ئیدیت") { editSource(source) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), dp(6), dp(4), 0) })
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

    private fun editSource(source: PlaylistSource) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val nameInput = edit("ناوی Playlist", source.name)
        panel.addView(nameInput)
        var valueInput: EditText? = null
        var extraInput: EditText? = null
        if (source.type == "url") {
            valueInput = edit("M3U / M3U8 URL", source.value); panel.addView(valueInput)
        } else if (source.type == "portal") {
            valueInput = edit("Portal URL", source.value); panel.addView(valueInput)
            extraInput = edit("MAC Address", source.extra); panel.addView(extraInput)
        }
        AlertDialog.Builder(this)
            .setTitle("ئیدیتی Playlist")
            .setView(panel)
            .setNegativeButton("پاشگەزبوونەوە", null)
            .setPositiveButton("پاشەکەوت") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) { toast("ناوی Playlist بەتاڵ نەبێت"); return@setPositiveButton }
                source.name = name
                valueInput?.let { source.value = it.text.toString().trim() }
                extraInput?.let { source.extra = it.text.toString().trim().uppercase() }
                if (source.type == "portal" && !source.extra.matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))) {
                    toast("MAC Address دروست نییە")
                    return@setPositiveButton
                }
                if (activeSourceId == source.id) activeSourceName = source.name
                saveSources(); renderSources()
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
                    categoryAfterSourceLoad = null
                    toast("نەتوانرا Playlist بکرێتەوە: ${e.localizedMessage ?: "هەڵە"}")
                }
            }
            "portal" -> {
                categoryAfterSourceLoad = null
                AlertDialog.Builder(this)
                    .setTitle(source.name)
                    .setMessage("ئەم MAC / Portal ـە پاشەکەوت کراوە، بەڵام پەخشکردنی کەناڵەکانی Stalker پێویستی بە جێبەجێکردنی API ـی Portal هەیە کە لەم وەشانەدا هێشتا چالاک نییە.")
                    .setPositiveButton("باشە", null).show()
            }
        }
    }

    private fun importPlaylistFile(uri: Uri) {
        showLoadingDialog("تکایە چاوەڕوان بە، فایلەکە دەخوێندرێتەوە و پشکنین دەکرێت...")
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (text.isBlank() || !text.contains("#EXTINF", true) || parsePlaylist(text).isEmpty()) {
                    showPlaylistStatus("ببورە، ئەم فایلە قبوڵ نەکرا؛ فایلەکە M3U/M3U8 ـی دروست نییە یان لینکێکی ڤیدیۆیی تێدا نییە.", false)
                    toast("فایلەکە قبوڵ نەکرا")
                    return@launch
                }
                val file = File(filesDir, "playlist_${UUID.randomUUID()}.m3u")
                withContext(Dispatchers.IO) { file.writeText(text) }
                val source = PlaylistSource(UUID.randomUUID().toString(), pendingFileName.ifBlank { "M3U ${sources.size + 1}" }, "file", file.absolutePath)
                activatePlaylist(text, source, returnHome = true)
                pendingFileName = ""
                showPlaylistSuccess("سەرکەوتوو بوو! فایلەکە قبوڵ کرا و پاشەکەوت کرا.")
            } catch (e: Exception) {
                showPlaylistStatus("ببورە، نەتوانرا فایلەکە بخوێندرێتەوە: ${e.localizedMessage ?: "هەڵەیەک ڕوویدا"}", false)
                toast("فایلەکە قبوڵ نەکرا")
            } finally {
                dismissLoadingDialog()
            }
        }
    }

    private fun loadUrl(url: String, source: PlaylistSource?, quiet: Boolean = false, returnHome: Boolean = true) {
        if (loading) {
            if (!quiet) toast("تکایە چاوەڕێ بکە")
            return
        }
        loading = true
        if (!quiet) showLoadingDialog()
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
                if (!text.contains("#EXTINF", true) || parsePlaylist(text).isEmpty()) {
                    throw Exception("ئەم لینکە Playlist ـی M3U/M3U8 ـی دروست نییە یان هیچ لینکێکی ڤیدیۆیی تێدا نییە")
                }
                val active = source ?: sources.firstOrNull { it.value == url }
                val acceptedSource = active ?: PlaylistSource(UUID.randomUUID().toString(), "Playlist ${sources.size + 1}", "url", url)
                activatePlaylist(text, acceptedSource, returnHome)
                // Do not report success unless parsing actually produced playable entries.
                if (items.isEmpty() || activeSourceId != acceptedSource.id) {
                    throw Exception("هیچ کەناڵێکی دروست لە Playlist ـەکەدا نەدۆزرایەوە")
                }
                lastUrl = url
                prefs.edit().putString("last_url", url).apply()
                if (!quiet) showPlaylistSuccess("سەرکەوتوو بوو! لینکەکە قبوڵ کرا و ${items.size} بڕگە دۆزرایەوە.")
            } catch (e: Exception) {
                categoryAfterSourceLoad = null
                if (!quiet) {
                    val message = "ببورە، نەتوانرا ئەم Playlist ـە داخڵ بکرێت. ${e.localizedMessage ?: "لینکەکە دروست نییە یان وەڵامی نەداوەتەوە"}"
                    showPlaylistStatus(message, false)
                    toast("ببورە، لینکەکە قبوڵ نەکرا")
                }
            } finally {
                loading = false
                if (!quiet) dismissLoadingDialog()
            }
        }
    }

    private fun activatePlaylist(text: String, source: PlaylistSource, returnHome: Boolean) {
        val parsed = parsePlaylist(text)
        if (parsed.isEmpty()) {
            categoryAfterSourceLoad = null
            showPlaylistStatus("ببورە، نەتوانی ئەم Playlist ـە داخڵ بکەیت؛ هیچ لینکێکی ڤیدیۆیی تێدا نەدۆزرایەوە.", false)
            toast("هیچ لینکێکی ڤیدیۆ لە Playlist ـەکەدا نەدۆزرایەوە")
            return
        }
        playlistText = text
        if (sources.none { it.id == source.id }) sources.add(source)
        saveSources()
        items.clear()
        items.addAll(parsed)
        activeSourceId = source.id
        activeSourceName = source.name
        if (source.type == "url") {
            lastUrl = source.value
            prefs.edit().putString("last_url", source.value).apply()
        }
        prefs.edit().putString("last_source_id", source.id).apply()
        if (returnHome) {
            categoryAfterSourceLoad = null
            buildHome()
        } else {
            val nextCategory = categoryAfterSourceLoad
            if (nextCategory != null) {
                categoryAfterSourceLoad = null
                buildCategoryPage(nextCategory)
            }
        }
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
            putExtra("overlay_timeout_seconds", prefs.getInt("overlay_timeout_seconds", 1))
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
        val timeoutLabel = TextView(this).apply {
            text = "ماوەی پیشاندانی ناوی کەناڵ و لیست (چرکە، ١ تا ٦٠):"
            textSize = 14f
            setTextColor(gold)
            setPadding(0, dp(14), 0, dp(4))
        }
        val timeoutInput = edit("ژمارەی چرکە", prefs.getInt("overlay_timeout_seconds", 1).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(18), dp(8), dp(18))
            addView(autoLoad)
            addView(autoRefresh)
            addView(fullscreen)
            addView(timeoutLabel)
            addView(timeoutInput)
        }
        root.addView(box)
        root.addView(button("پاشەکەوتکردنی ڕێکخستنەکان") {
            val timeoutSeconds = timeoutInput.text.toString().toIntOrNull()
            if (timeoutSeconds == null || timeoutSeconds !in 1..60) {
                toast("تکایە ژمارەیەک لە ١ تا ٦٠ چرکە بنووسە")
                return@button
            }
            prefs.edit()
                .putBoolean("auto_load", autoLoad.isChecked)
                .putBoolean("auto_refresh", autoRefresh.isChecked)
                .putBoolean("player_fullscreen", fullscreen.isChecked)
                .putInt("overlay_timeout_seconds", timeoutSeconds)
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

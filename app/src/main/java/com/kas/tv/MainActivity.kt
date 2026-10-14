package com.kas.tv

import android.app.Activity
import android.content.Intent
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

data class TvItem(val name: String, val url: String)

class MainActivity : AppCompatActivity() {
    private val items = mutableListOf<TvItem>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var sourceInput: EditText
    private val pickM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { parseAndShow(it.readText()) }
                    ?: showStatus("نەتوانرا فایلەکە بخوێندرێتەوە")
            } catch (e: Exception) { showStatus("هەڵە لە خوێندنەوەی فایل: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8,8,8)
        window.navigationBarColor = android.graphics.Color.rgb(8,8,8)
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(android.graphics.Color.rgb(10,10,10))
        }
        scroll.addView(page)
        setContentView(scroll)
        page.addView(TextView(this).apply {
            text = "KAS TV"; textSize = 38f; gravity = Gravity.CENTER
            setTextColor(0xFFF5B400.toInt()); setPadding(0, 8, 0, 4); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "Live TV  •  Movies  •  Series  •  Playlists"
            textSize = 15f; gravity = Gravity.CENTER; setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 0, 0, 18)
        })
        sourceInput = EditText(this).apply {
            hint = "M3U / M3U8 URL"; setSingleLine(true); setTextColor(-1); setHintTextColor(0xFF999999.toInt())
            setBackgroundColor(0xFF202020.toInt())
        }
        page.addView(sourceInput, matchWidth())
        addButton(page, "Load M3U URL") { loadUrl(sourceInput.text.toString().trim()) }
        addButton(page, "Choose local M3U file") { pickM3u.launch(arrayOf("*/*")) }
        page.addView(TextView(this).apply {
            text = "Xtream Codes"; textSize = 22f; setTextColor(0xFFF5B400.toInt()); setPadding(0, 20, 0, 4)
        })
        val server = field(page, "Server URL (http://host:port)")
        val username = field(page, "Username")
        val password = field(page, "Password")
        addButton(page, "Connect Xtream") {
            val s = server.text.toString().trim().trimEnd('/')
            val u = username.text.toString().trim()
            val p = password.text.toString()
            if (s.isBlank() || u.isBlank() || p.isBlank()) showStatus("Server، Username و Password پڕ بکەرەوە")
            else loadUrl("$s/get.php?username=${Uri.encode(u)}&password=${Uri.encode(p)}&type=m3u_plus&output=ts")
        }
        page.addView(TextView(this).apply {
            text = "MAC / Portal"; textSize = 22f; setTextColor(0xFFF5B400.toInt()); setPadding(0, 20, 0, 4)
        })
        field(page, "Portal URL")
        field(page, "MAC address")
        page.addView(TextView(this).apply {
            text = "تێبینی: پەیوەندی MAC/Portal پێویستی بە ڕێگای پشتگیریکراوی دابینکەرەکە هەیە؛ ئەم وەشانە تەنها خانەکان پیشان دەدات."
            textSize = 13f; setTextColor(0xFFBBBBBB.toInt()); setPadding(0, 6, 0, 10)
        })
        status = TextView(this).apply { textSize = 14f; setTextColor(0xFFF5B400.toInt()); setPadding(0, 12, 0, 12) }
        page.addView(status)
        page.addView(TextView(this).apply {
            text = "Channels"; textSize = 24f; setTextColor(-1); setPadding(0, 8, 0, 8)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        showStatus("M3U URL بنووسە یان فایلێکی M3U هەڵبژێرە.")
    }

    private fun field(parent: LinearLayout, hintText: String): EditText {
        val e = EditText(this).apply {
            hint = hintText; setSingleLine(true); setTextColor(-1); setHintTextColor(0xFF999999.toInt())
            setBackgroundColor(0xFF202020.toInt()); setPadding(14, 8, 14, 8)
        }
        parent.addView(e, matchWidth())
        return e
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(0xFF111111.toInt())
            setBackgroundColor(0xFFF5B400.toInt()); textSize = 16f
            setOnClickListener { action() }
        }
        val lp = matchWidth()
        lp.setMargins(0, 8, 0, 6)
        parent.addView(b, lp)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun loadUrl(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showStatus("URL ـێکی دروستی http:// یان https:// بنووسە"); return
        }
        showStatus("Playlist دەخوێندرێتەوە…")
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val conn = (URL(url).openConnection() as HttpURLConnection)
                    conn.connectTimeout = 15000; conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", "KAS-TV/1.0")
                    try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
                }
                parseAndShow(text)
            } catch (e: Exception) {
                showStatus("نەتوانرا playlist بخوێندرێتەوە: ${e.message}\nURL یان پەیوەندی دابینکەر بپشکنە.")
            }
        }
    }

    private fun parseAndShow(text: String) {
        items.clear()
        var name = "Channel"
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "Channel").trim().ifBlank { "Channel" }
                val tvg = Regex("""tvg-name="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
                if (!tvg.isNullOrBlank()) name = tvg
            } else if (line.startsWith("http://") || line.startsWith("https://")) {
                items.add(TvItem(name, line)); name = "Channel"
            }
        }
        list.removeAllViews()
        items.forEach { item ->
            val b = Button(this).apply {
                text = item.name; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(-1); setBackgroundColor(0xFF202020.toInt())
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, PlayerActivity::class.java).putExtra("url", item.url).putExtra("name", item.name))
                }
            }
            val lp = matchWidth(); lp.setMargins(0, 4, 0, 4); list.addView(b, lp)
        }
        showStatus("ژمارەی بابەتە بارکراوەکان: ${items.size}")
    }

    private fun showStatus(s: String) { if (::status.isInitialized) status.text = s }

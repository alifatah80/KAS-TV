package com.kas.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlaylistActivity : AppCompatActivity() {

    private val gold = Color.rgb(245, 180, 0)
    private val bg = Color.rgb(12, 13, 17)
    private val card = Color.rgb(29, 31, 39)
    private val white = Color.WHITE
    private val grey = Color.rgb(175, 179, 190)

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences("kas_tv_sources", MODE_PRIVATE)
    }

    private val sources = mutableListOf<JSONObject>()

    private val pickFile =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) importFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSources()
        buildScreen()
        renderSources()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
            setBackgroundColor(bg)
        }

        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = button("‹  Back", card, white)
        back.setOnClickListener { finish() }

        header.addView(back)

        val title = TextView(this).apply {
            text = "  KAS TV  |  PLAYLISTS"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        root.addView(header)

        status = TextView(this).apply {
            text = "لینک و فایلەکانت لێرە بەڕێوە ببە."
            textSize = 14f
            setTextColor(grey)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }

        root.addView(status)

        heading("➕  زیادکردنی M3U / M3U8")

        nameInput = input("ناوی Playlist")
        urlInput = input("M3U / M3U8 URL")

        root.addView(nameInput, params())
        root.addView(urlInput, params())

        addButton("＋  Add Playlist URL") {
            addUrl()
        }

        addButton("📁  زیادکردنی فایل") {
            pickFile.launch(arrayOf("*/*"))
        }

        heading("🔐  Xtream Codes")

        val server = input("Server URL")
        val username = input("Username")
        val password = input("Password")

        password.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        root.addView(server, params())
        root.addView(username, params())
        root.addView(password, params())

        addButton("Connect / Save Xtream") {
            val base = server.text.toString()
                .trim()
                .trimEnd('/')

            val user = username.text.toString().trim()
            val pass = password.text.toString()

            if (base.isBlank() || user.isBlank() || pass.isBlank()) {
                message("تکایە هەموو خانەکان پڕ بکەرەوە.")
            } else {
                val url =
                    "$base/get.php?username=${Uri.encode(user)}" +
                    "&password=${Uri.encode(pass)}" +
                    "&type=m3u_plus&output=ts"

                saveSource(
                    name = "Xtream - $user",
                    type = "url",
                    value = url
                )

                server.text.clear()
                username.text.clear()
                password.text.clear()

                message("سەرچاوەی Xtream پاشەکەوت کرا.")
                renderSources()
            }
        }

        heading("📡  MAC / Stalker Portal")

        val portal = input("Portal URL")
        val mac = input("MAC Address")

        root.addView(portal, params())
        root.addView(mac, params())

        addButton("＋  Save Portal / MAC") {
            val portalUrl = portal.text.toString().trim()
            val macAddress = mac.text.toString().trim()

            if (portalUrl.isBlank() || macAddress.isBlank()) {
                message("تکایە Portal URL و MAC Address بنووسە.")
            } else {
                saveSource(
                    name = "Portal - $macAddress",
                    type = "portal",
                    value = portalUrl,
                    extra = macAddress
                )

                portal.text.clear()
                mac.text.clear()

                message(
                    "زانیارییەکانی Portal پاشەکەوت کران. " +
                    "پەخشکردن پێویستی بە پشتگیری پرۆتۆکۆلی Portal هەیە."
                )

                renderSources()
            }
        }

        heading("📚  سەرچاوە پاشەکەوتکراوەکان")

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(list)
    }

    private fun addUrl() {
        val name = nameInput.text.toString().trim()
        val url = urlInput.text.toString().trim()

        if (url.isBlank() ||
            !(url.startsWith("http://", true) ||
              url.startsWith("https://", true))
        ) {
            message("تکایە URL ـێکی دروست بنووسە.")
            return
        }

        saveSource(
            name = name.ifBlank { "Playlist ${sources.size + 1}" },
            type = "url",
            value = url
        )

        nameInput.text.clear()
        urlInput.text.clear()

        message("Playlist زیاد و پاشەکەوت کرا.")
        renderSources()
    }

    private fun importFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }

            if (text.isNullOrBlank()) {
                message("فایلەکە بەتاڵە یان نەخوێندرایەوە.")
                return
            }

            if (!text.contains("#EXTINF", ignoreCase = true)) {
                message("فایلەکە وەک Playlist ـی M3U نەناسرا.")
                return
            }

            val fileName = "playlist_${System.currentTimeMillis()}.m3u"
            File(filesDir, fileName).writeText(text)

            saveSource(
                name = "File ${sources.size + 1}",
                type = "file",
                value = fileName
            )

            message("فایلەکە زیاد و پاشەکەوت کرا.")
            renderSources()

        } catch (e: Exception) {
            message("نەتوانرا فایلەکە زیاد بکرێت.")
        }
    }

    private fun saveSource(
        name: String,
        type: String,
        value: String,
        extra: String = ""
    ) {
        val item = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("name", name)
            put("type", type)
            put("value", value)
            put("extra", extra)
        }

        sources.add(item)
        persistSources()
    }

    private fun loadSources() {
        sources.clear()

        try {
            val json = JSONArray(
                prefs.getString("sources", "[]") ?: "[]"
            )

            for (i in 0 until json.length()) {
                sources.add(json.getJSONObject(i))
            }
        } catch (_: Exception) {
            sources.clear()
        }
    }

    private fun persistSources() {
        val json = JSONArray()

        sources.forEach { json.put(it) }

        prefs.edit()
            .putString("sources", json.toString())
            .apply()
    }

    private fun renderSources() {
        list.removeAllViews()

        if (sources.isEmpty()) {
            label("هیچ سەرچاوەیەک زیاد نەکراوە.")
            return
        }

        sources.forEach { item ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(card, 12)
            }

            val name = TextView(this).apply {
                text = item.optString("name")
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
            }

            val type = TextView(this).apply {
                text = when (item.optString("type")) {
                    "url" -> "M3U / M3U8 / Xtream"
                    "file" -> "M3U File"
                    "portal" -> "Stalker Portal / MAC"
                    else -> "Source"
                }

                textSize = 13f
                setTextColor(grey)
                setPadding(0, dp(4), 0, dp(8))
            }

            box.addView(name)
            box.addView(type)

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val use = button("Open / Use", gold, Color.BLACK)

            use.setOnClickListener {
                val kind = item.optString("type")

                if (kind == "portal") {
                    message(
                        "ئەم Portal ـە تەنها پاشەکەوت کراوە. " +
                        "بۆ پەخشکردن پێویستە Stalker API جێبەجێ بکرێت."
                    )
                    return@setOnClickListener
                }

                val result = Intent().apply {
                    putExtra("kind", kind)
                    putExtra("value", item.optString("value"))
                    putExtra("name", item.optString("name"))
                }

                setResult(Activity.RESULT_OK, result)
                finish()
            }

            val delete = button("Delete", Color.rgb(150, 35, 35), white)

            delete.setOnClickListener {
                val id = item.optString("id")
                val fileName = item.optString("value")

                if (item.optString("type") == "file") {
                    File(filesDir, fileName).delete()
                }

                sources.removeAll {
                    it.optString("id") == id
                }

                persistSources()
                renderSources()
                message("سەرچاوەکە سڕایەوە.")
            }

            actions.addView(
                use,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )

            actions.addView(
                delete,
                LinearLayout.LayoutParams(0, dp(44), 1f)
            )

            box.addView(actions)

            list.addView(
                box,
                LinearLayout.LayoutParams(
                    -1,
                    -2
                ).apply {
                    setMargins(0, dp(5), 0, dp(5))
                }
            )
        }
    }

    private fun heading(text: String) {
        root.addView(
            TextView(this).apply {
                this.text = text
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(gold)
                setPadding(dp(4), dp(14), dp(4), dp(8))
            }
        )
    }

    private fun label(text: String) {
        list.addView(
            TextView(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(grey)
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
        )
    }

    private fun input(hintText: String) =
        EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(grey)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(card, 8)
        }

    private fun addButton(
        text: String,
        action: () -> Unit
    ) {
        val b = button(text, gold, Color.BLACK)
        b.setOnClickListener { action() }

        root.addView(
            b,
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )
    }

    private fun button(
        text: String,
        color: Int,
        textColor: Int
    ) = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = rounded(color, 10)
        isFocusable = true
        isClickable = true
    }

    private fun params() =
        LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(4), 0, dp(4))
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun message(text: String) {
        status.text = text
    }
}

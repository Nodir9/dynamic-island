package dev.island.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandOverlay.init(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(12))
        }
        col.addView(TextView(this).apply {
            text = "Dynamic Island"
            textSize = 26f
        })
        col.addView(status)
        col.addView(btn("1. Доступ к уведомлениям") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(btn("2. Поверх других приложений") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        col.addView(btn("Показать тест") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала выдай разрешение «Поверх других приложений»", Toast.LENGTH_LONG).show()
            } else {
                IslandOverlay.demo()
            }
        })
        col.addView(TextView(this).apply {
            text = "\nПодгонка под вырез камеры:"
            textSize = 15f
        })
        col.addView(slider("Отступ сверху", "y", 0, 60, 11))
        col.addView(slider("Ширина в свёрнутом виде", "w", 40, 220, 90))
        col.addView(slider("Высота в свёрнутом виде", "h", 20, 60, 30))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val overlay = Settings.canDrawOverlays(this)
        status.text = "Доступ к уведомлениям: " + (if (listener) "включён ✅" else "выключен ❌") +
            "\nПоверх других приложений: " + (if (overlay) "включено ✅" else "выключено ❌")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit): View =
        Button(this).apply {
            setText(label)
            setOnClickListener { onClick() }
        }

    private fun slider(label: String, key: String, min: Int, max: Int, def: Int): View {
        val p = getSharedPreferences("island", MODE_PRIVATE)
        val tv = TextView(this)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = p.getInt(key, def) - min
        fun upd() {
            tv.text = "$label: ${sb.progress + min} dp"
        }
        upd()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                p.edit().putInt(key, v + min).apply()
                upd()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(tv)
            addView(sb)
        }
    }
}

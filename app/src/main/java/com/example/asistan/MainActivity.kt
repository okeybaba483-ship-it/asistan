package com.example.asistan

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var sp: SharedPreferences
    private lateinit var log: TextView
    private lateinit var spin: Spinner
    private lateinit var sw: Switch
    private lateinit var cmd: EditText
    private val fields = HashMap<String, EditText>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("a", 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }

        fun label(t: String) = root.addView(TextView(this).apply { text = t; textSize = 16f })
        fun btn(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        fun field(k: String, hint: String, def: String, secret: Boolean = false) {
            val e = EditText(this).apply {
                this.hint = hint; setText(sp.getString(k, def)); setSingleLine()
                if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            fields[k] = e; root.addView(e)
        }

        label("Asistan")
        spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("claude", "deepseek"))
            setSelection(if (sp.getString("prov", "claude") == "claude") 0 else 1)
        }
        root.addView(spin)
        field("claude_key", "Claude API anahtarı", "", true)
        field("claude_model", "Claude model", "claude-sonnet-4-5")
        field("ds_key", "DeepSeek API anahtarı", "", true)
        field("ds_model", "DeepSeek model", "deepseek-chat")
        sw = Switch(this).apply { text = "Sürekli dinle"; isChecked = sp.getBoolean("cont", false) }
        root.addView(sw)
        btn("Kaydet") { save(); toast("Kaydedildi") }
        btn("1) Erişilebilirlik ayarlarını aç") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2) Mikrofon izni ver") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }

        cmd = EditText(this).apply { hint = "Komut yaz" }
        root.addView(cmd)
        btn("Gönder") {
            save()
            val s = AgentService.inst
            if (s == null) toast("Önce erişilebilirlik iznini aç") else s.submit(cmd.text.toString()).also { cmd.setText("") }
        }
        btn("🎤 Konuş") {
            save()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            else AgentService.inst?.listen() ?: toast("Önce erişilebilirlik iznini aç")
        }
        btn("🧠 Hafızayı göster / sil") {
            AlertDialog.Builder(this).setTitle("Hafıza")
                .setMessage(sp.getString("mem", "").orEmpty().ifBlank { "(boş)" })
                .setPositiveButton("Tamam", null)
                .setNegativeButton("Sil") { _, _ -> sp.edit().remove("mem").apply() }.show()
        }
        log = TextView(this).apply { text = AgentService.buffer.toString() }
        root.addView(log)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        AgentService.onLog = { s -> runOnUiThread { log.append(s + "\n") } }
    }

    override fun onPause() { super.onPause(); AgentService.onLog = null; save() }

    private fun save() {
        sp.edit().apply {
            fields.forEach { (k, e) -> putString(k, e.text.toString().trim()) }
            putString("prov", spin.selectedItem as String)
            putBoolean("cont", sw.isChecked)
        }.apply()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}

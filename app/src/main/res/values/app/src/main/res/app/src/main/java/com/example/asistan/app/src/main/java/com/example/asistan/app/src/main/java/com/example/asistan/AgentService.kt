package com.example.asistan

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentService : AccessibilityService() {
    companion object {
        var inst: AgentService? = null
        var onLog: ((String) -> Unit)? = null
        val buffer = StringBuilder()
    }

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var btn: TextView? = null
    private val history = ArrayList<Pair<String, String>>()
    @Volatile private var busy = false
    private val sp get() = getSharedPreferences("a", 0)

    // ---------- yaşam döngüsü ----------
    override fun onServiceConnected() {
        inst = this
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) { tts?.language = Locale("tr", "TR"); ttsReady = true }
        }
        val b = TextView(this).apply {
            text = "🎤"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC1565C0.toInt()) }
            setOnClickListener { listen() }
        }
        val sz = (56 * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            sz, sz, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(b, lp)
        btn = b
        log("Asistan hazır. Mavi mikrofon düğmesine dokunup konuş.")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        btn?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        recognizer?.destroy(); tts?.shutdown(); inst = null
        return super.onUnbind(intent)
    }

    // ---------- yardımcılar ----------
    fun log(s: String) {
        buffer.append(s).append('\n')
        onLog?.invoke(s)
    }

    private fun speak(t: String) { if (ttsReady) tts?.speak(t, TextToSpeech.QUEUE_ADD, null, null) }
    private fun setBtn(t: String) = main.post { btn?.text = t }

    // ---------- ses ----------
    fun listen() {
        main.post {
            if (busy) return@post
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                log("⚠ Mikrofon izni yok (uygulamadan ver)"); return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) { log("⚠ Konuşma tanıma servisi yok"); return@post }
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    setBtn("🎤")
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) again() else submit(t)
                }
                override fun onError(e: Int) {
                    setBtn("🎤")
                    if (e != SpeechRecognizer.ERROR_CLIENT) again()
                }
                override fun onReadyForSpeech(p: Bundle?) { setBtn("👂") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            })
        }
    }

    private fun again() {
        if (sp.getBoolean("cont", false) && !busy) main.postDelayed({ listen() }, 800)
    }

    // ---------- ana akış ----------
    fun submit(text: String) {
        if (busy) { log("⏳ Önceki iş sürüyor"); return }
        busy = true
        setBtn("⏳")
        log("🧑 $text")
        Thread {
            try { run(text) } catch (e: Exception) {
                log("⚠ ${e.message}"); speak("Bir hata oldu.")
            }
            busy = false
            setBtn("🎤")
            while (ttsReady && tts?.isSpeaking == true) Thread.sleep(200)
            again()
        }.start()
    }

    private fun system(): String = """
Sen Türkçe konuşan bir telefon asistanısın. Kullanıcının ekranını görüyorsun ve telefonu onun adına kullanabilirsin.
SADECE JSON döndür: {"say":"kısa sesli yanıt","actions":[...],"remember":"kalıcı bilgi ya da boş","confirm":"riskli işlem sorusu ya da boş"}
Eylemler: {"type":"tap","x":0,"y":0}, {"type":"tap_text","text":"..."}, {"type":"type","text":"..."}, {"type":"swipe","dir":"up|down|left|right"}, {"type":"back"}, {"type":"home"}, {"type":"open_app","name":"..."}, {"type":"done"}
İş bitince ya da sadece sohbetse actions boş olsun veya done ver. Ekran değişince bir sonraki adımda yeni ekranı görürsün.
Ödeme, mesaj/e-posta gönderme, silme, satın alma gibi geri dönüşsüz işlerden önce "confirm" alanını doldur; onaylanmazsa eylem yapılmaz.
Kullanıcının tercihlerini, alışkanlıklarını ve düzeltmelerini kısa cümleyle "remember" ile kaydet.
Yanıtların kısa olsun, sesli okunacak. Ekran görüntüsü 540px genişliğe küçültülmüş olabilir; koordinat için EKRAN listesindeki [x,y] değerlerini kullan.

HAFIZA:
${sp.getString("mem", "")}
""".trimIndent()

    private fun run(cmd: String) {
        val prov = sp.getString("prov", "claude") ?: "claude"
        val dm = resources.displayMetrics
        var input = "Kullanıcı: $cmd"
        var finalSay = ""
        for (step in 1..6) {
            val tree = dump()
            val img = if (prov == "claude") shot() else null
            val user = "$input\n\nEKRAN (${dm.widthPixels}x${dm.heightPixels}):\n$tree"
            val raw = Llm.call(this, system(), history.takeLast(6), user, img)
            val j = parse(raw)
            val say = j.optString("say")
            if (say.isNotBlank()) { log("🤖 $say"); speak(say); finalSay = say }
            val mem = j.optString("remember")
            if (mem.isNotBlank()) remember(mem)
            val acts = j.optJSONArray("actions")
            if (acts == null || acts.length() == 0) break
            val q = j.optString("confirm")
            if (q.isNotBlank() && !confirm(q)) { log("🚫 İptal edildi"); speak("İptal ettim."); break }
            var done = false
            for (i in 0 until acts.length()) {
                val a = acts.getJSONObject(i)
                if (a.optString("type") == "done") { done = true; break }
                log("⚙ ${a}")
                exec(a)
                Thread.sleep(700)
            }
            if (done) break
            Thread.sleep(1200)
            input = "Kullanıcı isteği: $cmd\nEylemler uygulandı. Ekranı kontrol et; iş bittiyse done ver."
        }
        history.add(cmd to finalSay)
    }

    private fun parse(raw: String): JSONObject = try {
        JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
    } catch (e: Exception) { JSONObject().put("say", raw.take(300)) }

    private fun remember(r: String) {
        val l = (sp.getString("mem", "") ?: "").split("\n").filter { it.isNotBlank() } + r
        sp.edit().putString("mem", l.takeLast(80).joinToString("\n")).apply()
        log("🧠 Hafıza: $r")
    }

    private fun confirm(q: String): Boolean {
        speak(q)
        val l = CountDownLatch(1)
        var ok = false
        main.post {
            val ctx = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            val d = AlertDialog.Builder(ctx).setMessage(q).setCancelable(false)
                .setPositiveButton("Evet") { _, _ -> ok = true; l.countDown() }
                .setNegativeButton("Hayır") { _, _ -> l.countDown() }.create()
            d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            d.show()
        }
        l.await(60, TimeUnit.SECONDS)
        return ok
    }

    // ---------- ekranı okuma ----------
    private fun dump(): String {
        val r = rootInActiveWindow ?: return "(ekran okunamadı)"
        val sb = StringBuilder()
        walk(r, sb)
        return sb.toString().take(6000)
    }

    private fun walk(n: AccessibilityNodeInfo, sb: StringBuilder) {
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank() || n.isClickable || n.isEditable) {
            val b = Rect(); n.getBoundsInScreen(b)
            sb.append("[${b.centerX()},${b.centerY()}] ${t ?: ""}")
            if (n.isClickable) sb.append(" (tık)")
            if (n.isEditable) sb.append(" (giriş)")
            sb.append('\n')
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, sb) }
    }

    private fun shot(): String? {
        val l = CountDownLatch(1)
        var out: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hb = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)!!
                    val bm = hb.copy(Bitmap.Config.ARGB_8888, false)
                    val sc = Bitmap.createScaledBitmap(bm, 540, (540f * bm.height / bm.width).toInt(), true)
                    val bo = ByteArrayOutputStream()
                    sc.compress(Bitmap.CompressFormat.JPEG, 70, bo)
                    out = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
                    r.hardwareBuffer.close()
                } catch (e: Exception) { }
                l.countDown()
            }
            override fun onFailure(c: Int) { l.countDown() }
        })
        l.await(5, TimeUnit.SECONDS)
        return out
    }

    // ---------- eylemler ----------
    private fun exec(a: JSONObject) {
        when (a.optString("type")) {
            "tap" -> tap(a.optInt("x"), a.optInt("y"))
            "tap_text" -> tapText(a.optString("text"))
            "type" -> typeText(a.optString("text"))
            "swipe" -> swipe(a.optString("dir"))
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "open_app" -> openApp(a.optString("name"))
        }
    }

    private fun gesture(p: Path, dur: Long) {
        val l = CountDownLatch(1)
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { l.countDown() }
                override fun onCancelled(g: GestureDescription?) { l.countDown() }
            }, null
        )
        l.await(3, TimeUnit.SECONDS)
    }

    private fun tap(x: Int, y: Int) = gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 60)

    private fun swipe(dir: String) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val p = Path()
        when (dir) {
            "up" -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.25f) }
            "down" -> { p.moveTo(w / 2, h * 0.25f); p.lineTo(w / 2, h * 0.75f) }
            "left" -> { p.moveTo(w * 0.8f, h / 2); p.lineTo(w * 0.2f, h / 2) }
            else -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.8f, h / 2) }
        }
        gesture(p, 300)
    }

    private fun tapText(t: String) {
        val n = rootInActiveWindow?.findAccessibilityNodeInfosByText(t)?.firstOrNull() ?: return
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if (c != null) c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        else { val b = Rect(); n.getBoundsInScreen(b); tap(b.centerX(), b.centerY()) }
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findEditable(c)?.let { return it } }
        return null
    }

    private fun typeText(t: String) {
        val root = rootInActiveWindow ?: return
        val n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(q, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name.lowercase()) } ?: run {
            log("⚠ Uygulama bulunamadı: $name"); return
        }
        pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
        }
    }
}

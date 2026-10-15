package com.sindhikeyboard.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

object SindhiData {

    /** Every Sindhi letter, textbook order, 11 per row (5 rows = 55 keys). */
    val letters: List<String> = listOf(
        "\u0622", "\u0627", "\u0628", "\u067B", "\u0680", "\u067E", "\u062A", "\u067F", "\u067D", "\u067A", "\u0679",
        "\u062B", "\u062C", "\u0684", "\u062C\u06BE", "\u0683", "\u0687", "\u0686", "\u062D", "\u062E", "\u062F", "\u068C",
        "\u068F", "\u068A", "\u068D", "\u0630", "\u0631", "\u0699", "\u0632", "\u0633", "\u0634", "\u0635", "\u0636",
        "\u0637", "\u0638", "\u0639", "\u063A", "\u0641", "\u06A6", "\u0642", "\u06AA", "\u06A9", "\u06AF", "\u06B3",
        "\u06AF\u06BE", "\u06B1", "\u0644", "\u0645", "\u0646", "\u06BB", "\u0648", "\u0647", "\u06BE", "\u0621", "\u064A"
    )

    /** Quick phrases in the bar above the keys. Tap = insert. */
    val phrases: List<String> = listOf(
        "ماشاءَ الله",
        "سُبحان الله",
        "الحمدُ لله",
        "ان شاءَ الله",
        "الله اڪبر",
        "بسم الله الرحمٰن الرحيم",
        "آمين",
        "السلام عليڪم",
        "وعليڪم السلام",
        "جزاڪ الله",
        "استغفرالله",
        "ﷺ",
        "لا اله الا الله",
        "الله حافظ",
        "الله پاڪ",
        "الله خير ڪندو",
        "الله توهان کي سلامت رکي",
        "ڀلي ڪري آيا",
        "مهرباني",
        "ٿورائتو",
        "وڏي مهرباني",
        "ڪيئن آهيو؟",
        "مان ٺيڪ آهيان",
        "عيد مبارڪ",
        "جمعو مبارڪ",
        "رمضان مبارڪ",
        "سالگرهه مبارڪ",
        "صبح بخير",
        "شب بخير",
        "خدا حافظ",
        "سائين",
        "جيئي سنڌ"
    )

    val emoji: List<String> = listOf(
        "😊", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤔",
        "😢", "😭", "😡", "😴", "🤗", "🙃", "😇", "🥳",
        "👍", "👎", "👏", "🙏", "🤲", "💪", "👌", "🤝",
        "❤️", "💔", "🌹", "⭐", "🔥", "🎉", "🕌", "📿",
        "🌙", "🕋", "☀️", "🌸", "💐", "🇵🇰", "😅", "😉"
    )

    val digits: List<String> = listOf(
        "\u06F1", "\u06F2", "\u06F3", "\u06F4", "\u06F5",
        "\u06F6", "\u06F7", "\u06F8", "\u06F9", "\u06F0"
    )

    val harakat: List<Pair<String, String>> = listOf(
        "\u0640\u064E" to "\u064E",
        "\u0640\u0650" to "\u0650",
        "\u0640\u064F" to "\u064F",
        "\u0640\u0651" to "\u0651",
        "\u0640\u0652" to "\u0652",
        "\u0640\u064B" to "\u064B",
        "\u0640\u064D" to "\u064D",
        "\u0640\u064C" to "\u064C",
        "\u0640\u0670" to "\u0670",
        "\u0640\u0653" to "\u0653"
    )

    val punctuation: List<String> = listOf(
        "\u060C", "\u061B", "\u061F", "\u06D4", ".", "!", "(", ")", "\u00AB", "\u00BB"
    )

    val symbols: List<String> = listOf(
        "\uFDFA", "\uFDF2", "\uFDFD", "*", "+", "=", "_", "-", ":", "/"
    )
}

class SindhiIME : InputMethodService() {

    companion object {
        const val PREFS = "sindhi_kb"
        private const val NORMAL = 0
        private const val SPECIAL = 1
        private const val ACCENT = 2
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private enum class Layer { SINDHI, SYMBOLS, ENGLISH, EMOJI }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private lateinit var root: LinearLayout
    private lateinit var phraseScroll: HorizontalScrollView
    private lateinit var phraseBar: LinearLayout
    private lateinit var body: LinearLayout

    private var layer = Layer.SINDHI
    private var shift = false
    private var rowH = 0

    private var cBg = 0
    private var cKey = 0
    private var cKeyP = 0
    private var cSpecial = 0
    private var cSpecialP = 0
    private var cAccent = 0
    private var cAccentP = 0
    private var cText = 0

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (dark) {
            cBg = Color.parseColor("#1B1F23"); cKey = Color.parseColor("#3A4046")
            cKeyP = Color.parseColor("#59626B"); cSpecial = Color.parseColor("#2A3036")
            cSpecialP = Color.parseColor("#59626B"); cText = Color.parseColor("#F2F2F2")
        } else {
            cBg = Color.parseColor("#D9DEE3"); cKey = Color.WHITE
            cKeyP = Color.parseColor("#B9C2CB"); cSpecial = Color.parseColor("#C2CAD2")
            cSpecialP = Color.parseColor("#A3AEB9"); cText = Color.parseColor("#1B1F23")
        }
        cAccent = Color.parseColor("#0B6E4F")
        cAccentP = Color.parseColor("#09523B")

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(cBg)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        phraseBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        phraseScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(phraseBar, FrameLayout.LayoutParams(WRAP, MATCH))
        }
        root.addView(phraseScroll, LinearLayout.LayoutParams(MATCH, dp(40)))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))

        buildPhrases()
        showLayer(Layer.SINDHI)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        buildPhrases()
        shift = false

        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val latinOnly = cls == InputType.TYPE_CLASS_TEXT && when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
        val target = when {
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE -> Layer.SYMBOLS
            latinOnly -> Layer.ENGLISH
            restarting -> layer
            else -> Layer.SINDHI
        }
        showLayer(target)
    }

    // ---------------------------------------------------------------- phrases bar

    private fun buildPhrases() {
        phraseBar.removeAllViews()
        val custom = (prefs.getString("custom_phrases", "") ?: "")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        (custom + SindhiData.phrases).forEach { phraseBar.addView(chip(it)) }
    }

    private fun chip(phrase: String): TextView = TextView(this).apply {
        text = phrase
        maxLines = 1
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.WHITE)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(cAccent)
        }
        layoutParams = LinearLayout.LayoutParams(WRAP, MATCH).apply {
            setMargins(dp(3), dp(4), dp(3), dp(4))
        }
        setOnClickListener {
            if (prefs.getBoolean("haptic", true)) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            commit("$phrase ")
        }
    }

    // ---------------------------------------------------------------- layers

    private fun showLayer(l: Layer) {
        layer = l
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rowH = dp(if (land) 32 else 38)
        phraseScroll.layoutParams = LinearLayout.LayoutParams(MATCH, dp(if (land) 32 else 40))
        body.removeAllViews()

        when (l) {
            Layer.SINDHI -> {
                SindhiData.letters.chunked(11).forEach { chunk ->
                    body.addView(row(true, chunk.map { letterKey(it) }))
                }
                body.addView(row(false, listOf(
                    layerKey("۱۲۳", Layer.SYMBOLS, 1.3f),
                    layerKey("😊", Layer.EMOJI, 1.1f),
                    globeKey(),
                    key("،", 1f) { commit("،") },
                    key("سنڌي", 3.4f, 14f) { commit(" ") },
                    backspaceKey(),
                    enterKey()
                )))
            }

            Layer.SYMBOLS -> {
                body.addView(row(false, SindhiData.digits.map { d -> key(d) { commit(d) } }))
                body.addView(row(false, SindhiData.harakat.map { (label, mark) ->
                    key(label, 1f, 20f, SPECIAL) { commit(mark) }
                }))
                body.addView(row(false, SindhiData.punctuation.map { p -> key(p) { commit(p) } }))
                body.addView(row(false, SindhiData.symbols.map { s -> key(s) { commit(s) } }))
                body.addView(row(false, listOf(
                    layerKey("اکر", Layer.SINDHI, 1.5f),
                    layerKey("ABC", Layer.ENGLISH, 1.3f),
                    layerKey("😊", Layer.EMOJI, 1.1f),
                    key("space", 3.4f, 13f) { commit(" ") },
                    backspaceKey(),
                    enterKey()
                )))
            }

            Layer.ENGLISH -> {
                fun lab(c: Char) = if (shift) c.uppercaseChar().toString() else c.toString()
                body.addView(row(false, "1234567890".map { c -> key(c.toString()) { typed(c.toString()) } }))
                body.addView(row(false, "qwertyuiop".map { c -> key(lab(c)) { typed(lab(c)) } }))
                body.addView(row(false,
                    listOf(spacer(0.5f)) +
                        "asdfghjkl".map { c -> key(lab(c)) { typed(lab(c)) } } +
                        listOf(spacer(0.5f))))
                body.addView(row(false,
                    listOf(key("⇧", 1.5f, 20f, if (shift) ACCENT else SPECIAL) {
                        shift = !shift
                        showLayer(Layer.ENGLISH)
                    }) +
                        "zxcvbnm".map { c -> key(lab(c)) { typed(lab(c)) } } +
                        listOf(backspaceKey(1.5f))))
                body.addView(row(false, listOf(
                    layerKey("سنڌي", Layer.SINDHI, 1.5f),
                    layerKey("۱۲۳", Layer.SYMBOLS, 1.2f),
                    globeKey(),
                    key(",", 1f) { commit(",") },
                    key("English", 3.2f, 13f) { commit(" ") },
                    key(".", 1f) { commit(".") },
                    enterKey()
                )))
            }

            Layer.EMOJI -> {
                SindhiData.emoji.chunked(8).forEach { chunk ->
                    body.addView(row(false, chunk.map { e -> key(e, 1f, 22f) { commit(e) } }))
                }
                body.addView(row(false, listOf(
                    layerKey("اکر", Layer.SINDHI, 1.5f),
                    layerKey("ABC", Layer.ENGLISH, 1.3f),
                    key(" ", 4f) { commit(" ") },
                    backspaceKey(),
                    enterKey()
                )))
            }
        }
    }

    // ---------------------------------------------------------------- key factories

    private fun row(rtl: Boolean, views: List<View>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        layoutParams = LinearLayout.LayoutParams(MATCH, rowH)
        views.forEach { addView(it) }
    }

    private fun spacer(w: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, MATCH, w)
    }

    private fun keyBg(fill: Int, pressed: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(c)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(fill))
        }
    }

    private fun key(
        label: String,
        weight: Float = 1f,
        size: Float = 19f,
        style: Int = NORMAL,
        repeat: Boolean = false,
        onKey: () -> Unit
    ): TextView {
        val fill: Int
        val pressed: Int
        when (style) {
            SPECIAL -> { fill = cSpecial; pressed = cSpecialP }
            ACCENT -> { fill = cAccent; pressed = cAccentP }
            else -> { fill = cKey; pressed = cKeyP }
        }
        return TextView(this).apply {
            text = label
            maxLines = 1
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(if (style == ACCENT) Color.WHITE else cText)
            background = keyBg(fill, pressed)
            layoutParams = LinearLayout.LayoutParams(0, MATCH, weight).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            var loop: Runnable? = null
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        if (prefs.getBoolean("haptic", true)) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onKey()
                        if (repeat) {
                            val r = object : Runnable {
                                override fun run() {
                                    onKey()
                                    handler.postDelayed(this, 50)
                                }
                            }
                            loop = r
                            handler.postDelayed(r, 400)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        loop?.let { handler.removeCallbacks(it) }
                        loop = null
                    }
                }
                true
            }
        }
    }

    private fun letterKey(s: String) = key(s, 1f, if (s.length > 1) 16f else 19f) { typed(s) }

    private fun layerKey(label: String, target: Layer, w: Float) =
        key(label, w, 15f, SPECIAL) { showLayer(target) }

    private fun backspaceKey(w: Float = 1.4f) =
        key("⌫", w, 20f, SPECIAL, repeat = true) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }

    private fun enterKey(w: Float = 1.4f) = key("⏎", w, 20f, ACCENT) { doEnter() }

    private fun globeKey() = key("🌐", 1.1f, 17f, SPECIAL) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------------------------------------------------------------- actions

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun typed(s: String) {
        commit(s)
        if (layer == Layer.ENGLISH && shift) {
            shift = false
            showLayer(Layer.ENGLISH)
        }
    }

    private fun doEnter() {
        val ic = currentInputConnection ?: return
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }
}

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SindhiIME.PREFS, MODE_PRIVATE)
        val green = Color.parseColor("#0B6E4F")

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        fun title(t: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        }

        col.addView(title("سنڌي ڪيبورڊ", 30f, green, true))
        col.addView(title("Sindhi Keyboard", 16f, Color.DKGRAY, false))

        col.addView(title("Step 1 · قدم 1", 15f, green, true))
        col.addView(button("Enable keyboard · ڪيبورڊ چالو ڪريو") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        col.addView(title("Step 2 · قدم 2", 15f, green, true))
        col.addView(button("Choose keyboard · ڪيبورڊ چونڊيو") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        col.addView(title("Test · آزمائش", 15f, green, true))
        col.addView(EditText(this).apply {
            hint = "Type here · هتي لکو"
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        })

        col.addView(title("Settings · سيٽنگون", 15f, green, true))
        col.addView(CheckBox(this).apply {
            text = "Vibrate on key press · ڪي دٻائڻ تي وائبريشن"
            isChecked = prefs.getBoolean("haptic", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("haptic", on).apply() }
        })

        col.addView(title("My phrases · منهنجا جملا", 15f, green, true))
        col.addView(title("One phrase per line. They appear first in the phrase bar.", 13f, Color.DKGRAY, false))
        val phrases = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setText(prefs.getString("custom_phrases", ""))
        }
        col.addView(phrases)
        col.addView(button("Save · محفوظ ڪريو") {
            prefs.edit().putString("custom_phrases", phrases.text.toString()).apply()
            Toast.makeText(this, "Saved ✓", Toast.LENGTH_SHORT).show()
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }
}

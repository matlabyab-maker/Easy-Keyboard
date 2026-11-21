package com.easykeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import android.inputmethodservice.InputMethodService

class EasyKeyboardService : InputMethodService() {
    private var persian = true
    private var caps = false
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 2, 2, 2)
            setBackgroundColor(Color.rgb(190, 198, 208))
        }

        // One horizontally scrolling toolbar. Answer stays at the far right,
        // with File immediately to its left.
        val toolbarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val toolbarKeys = listOf("«", "»", "؟", "Undo", "Redo", "Past", "Copy", "File", "Answer")
        toolbarKeys.forEach { label ->
            toolbar.addView(key(label, true) {
                when (label) {
                    "Undo" -> undo()
                    "Redo" -> redo()
                    "Past" -> pasteFromClipboard()
                    "Copy" -> copySelection()
                    "File" -> {
                        val intent = android.content.Intent(this, FilePickerActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    "Answer" -> answerCurrentEditor()
                    else -> currentInputConnection?.commitText(label, 1)
                }
            }, LinearLayout.LayoutParams(
                if (label == "Answer") dp(82) else dp(if (label.length > 4) 64 else 48),
                -1
            ))
        }
        toolbarScroll.addView(toolbar)
        outer.addView(toolbarScroll, LinearLayout.LayoutParams(-1, 48))

        val rows = if (persian) listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"),
            listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"),
            listOf("ظ", "ط", "ز", "ر", "ذ", "د", "پ", "و", "ژ", "چ", "ج")
        ) else listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("Z", "X", "C", "V", "B", "N", "M")
        )

        rows.forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (index == 2) {
                row.addView(key("Shift", true) {
                    caps = !caps
                    setInputView(buildKeyboard())
                }, LinearLayout.LayoutParams(0, -1, 1.35f))
            }
            chars.forEach { ch ->
                val shown = if (!persian && caps) ch.uppercase() else if (!persian) ch.lowercase() else ch
                val letterKey = key(shown, false) { typeText(shown) }
                val variants = if (persian) when (ch) {
                    "ا" -> listOf("آ", "أ", "إ")
                    "و" -> listOf("ؤ")
                    "ی" -> listOf("ئ", "ي", "ى")
                    else -> emptyList()
                } else emptyList()
                if (variants.isNotEmpty()) {
                    letterKey.setOnLongClickListener {
                        showLetterVariants(letterKey, variants)
                        true
                    }
                    letterKey.contentDescription = "$shown - hold for variants"
                }
                row.addView(letterKey, LinearLayout.LayoutParams(0, -1, 1f))
            }
            if (index == 0) {
                row.addView(key("Backspace", true) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }, LinearLayout.LayoutParams(0, -1, 1.8f))
            }
            if (index == 1) {
                row.addView(key("Enter", true) { answerCurrentEditor() }, LinearLayout.LayoutParams(0, -1, 1.8f))
            }
            outer.addView(row, LinearLayout.LayoutParams(-1, 61))
        }

        // Bottom-row keys are half their previous proportional width.
        // The row remains 58dp high; arrows are placed after Space in the
        // requested left/right/up/down order.
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Keep the requested physical left-to-right key order on RTL devices.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        // Match horizontal arrow keys to the Persian Shift key width (1.35 key units).
        // The Persian third row has 11 letter keys plus Shift (1.35 units).
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val letterUnit = ((screenWidthDp - 4f) / 12.35f).coerceAtLeast(dp(18).toFloat())
        val shiftWidth = (letterUnit * 1.35f).toInt()
        val verticalArrowWidth = (letterUnit * 0.8f).toInt()
        val bottomUnit = (resources.displayMetrics.widthPixels / resources.displayMetrics.density / 14f).toInt().coerceAtLeast(dp(18))
        fun addBottom(label: String, widthPx: Int, action: () -> Unit) {
            bottom.addView(key(label, true, action), LinearLayout.LayoutParams(widthPx, -1))
        }
        addBottom("123", bottomUnit) {
            Toast.makeText(this, "Number row is not available yet", Toast.LENGTH_SHORT).show()
        }
        addBottom(if (persian) "EN" else "FA", bottomUnit) {
            persian = !persian
            caps = false
            setInputView(buildKeyboard())
        }
        addBottom("Space", bottomUnit * 3) { typeText(" ") }
        addBottom(".", bottomUnit) { typeText(".") }
        addBottom("←", shiftWidth) { sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }
        addBottom("→", shiftWidth) { sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }
        addBottom("↑", verticalArrowWidth) { sendArrow(KeyEvent.KEYCODE_DPAD_UP) }
        addBottom("↓", verticalArrowWidth) { sendArrow(KeyEvent.KEYCODE_DPAD_DOWN) }
        addBottom("😊", bottomUnit) { typeText("😊") }
        outer.addView(bottom, LinearLayout.LayoutParams(-1, 58))
        return outer
    }

    private fun key(label: String, dark: Boolean, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = when {
                label.length >= 8 -> 9f
                label.length >= 5 -> 11f
                label.length >= 4 -> 12f
                else -> 18f
            }
            setTextColor(if (label == "Backspace") Color.rgb(105, 25, 25) else if (dark) Color.WHITE else Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            isSoundEffectsEnabled = false
            fun makeKeyBackground(color: Int) = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 7f
                setStroke(1, Color.rgb(105, 116, 130))
            }
            val normalColor = if (label == "Backspace") Color.rgb(244, 185, 185) else if (dark) Color.rgb(28, 65, 111) else Color.rgb(247, 249, 252)
            background = makeKeyBackground(normalColor)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.background = makeKeyBackground(Color.rgb(255, 220, 35))
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.background = makeKeyBackground(normalColor)
                }
                false
            }
            setOnClickListener { action() }
        }

    private fun showLetterVariants(anchor: View, variants: List<String>) {
        val popupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.rgb(190, 198, 208))
        }
        val popup = android.widget.PopupWindow(
            popupRow,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(56),
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        variants.forEach { variant ->
            popupRow.addView(key(variant, false) {
                typeText(variant)
                popup.dismiss()
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        popup.showAsDropDown(anchor, 0, -anchor.height - dp(60), Gravity.TOP or Gravity.START)
    }

    private fun answerCurrentEditor() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED

        // Prefer the action requested by the focused editor (Send/Go/Search/Done).
        if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
            if (connection.performEditorAction(action)) return
        }
        // Some web editors expose no IME action. Try Send, then a real Enter key event.
        if (connection.performEditorAction(EditorInfo.IME_ACTION_SEND)) return
        val now = System.currentTimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        connection.sendKeyEvent(KeyEvent(System.currentTimeMillis(), now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }

    private fun sendArrow(keyCode: Int) {
        val connection = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        connection.sendKeyEvent(KeyEvent(System.currentTimeMillis(), now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun copySelection() {
        val selected = currentInputConnection?.getSelectedText(0)?.toString()
        if (selected.isNullOrEmpty()) {
            Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied text", selected))
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) typeText(text)
    }

    private fun typeText(text: String) {
        currentInputConnection?.let {
            undoStack.add(text)
            redoStack.clear()
            it.commitText(text, 1)
        }
    }

    private fun undo() {
        val last = undoStack.removeLastOrNull() ?: return
        currentInputConnection?.deleteSurroundingText(last.length, 0)
        redoStack.add(last)
    }

    private fun redo() {
        val last = redoStack.removeLastOrNull() ?: return
        currentInputConnection?.commitText(last, 1)
        undoStack.add(last)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

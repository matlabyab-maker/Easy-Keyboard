package com.easykeyboard.app

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

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
        val toolbarScroll = ScrollView(this).apply { isFillViewport = true }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("«", "»", "؟", "←", "→", "↑", "↓", "UnDo", "ReDo", "File", "Answer").forEach { label ->
            toolbar.addView(key(label, true) {
                when(label) {
                    "UnDo" -> undo()
                    "ReDo" -> redo()
                    "File" -> {
                        val i = android.content.Intent(this, FilePickerActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    }
                    "Answer" -> {
                        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
                        if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) currentInputConnection?.performEditorAction(action)
                        else currentInputConnection?.commitText("\n", 1)
                    }
                    else -> currentInputConnection?.commitText(label, 1)
                }
            }, LinearLayout.LayoutParams(0, -1, if(label == "Answer") 2.1f else 1f))
        }
        toolbarScroll.addView(toolbar)
        outer.addView(toolbarScroll, LinearLayout.LayoutParams(-1, 48))

        val rows = if (persian) listOf(
            listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح"),
            listOf("ش","س","ی","ب","ل","ا","ت","ن","م","ک","گ"),
            listOf("ظ","ط","ز","ر","ذ","د","پ","و","چ")
        ) else listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L"),
            listOf("Z","X","C","V","B","N","M")
        )
        rows.forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if(index == 2) row.addView(key("⇧", true) { caps = !caps; setInputView(buildKeyboard()) }, LinearLayout.LayoutParams(0, -1, 1f))
            chars.forEach { ch ->
                val shown = if (!persian && caps) ch.uppercase() else if (!persian) ch.lowercase() else ch
                row.addView(key(shown, false) { typeText(shown) }, LinearLayout.LayoutParams(0, -1, 1f))
            }
            if(index == 0) row.addView(key("⌫", true) { currentInputConnection?.deleteSurroundingText(1,0) }, LinearLayout.LayoutParams(0, -1, 1.55f))
            if(index == 1) row.addView(key("↵", true) { currentInputConnection?.commitText("\n",1) }, LinearLayout.LayoutParams(0, -1, 1.55f))
            outer.addView(row, LinearLayout.LayoutParams(-1, 61))
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("123", "🌐", if(persian) "Fa" else "En", "فاصله", "😊").forEach { label ->
            bottom.addView(key(label, true) {
                when(label) {
                    "🌐", "Fa", "En" -> { persian = !persian; setInputView(buildKeyboard()) }
                    "فاصله" -> typeText(" ")
                    "123" -> Toast.makeText(this, "ردیف اعداد در نسخه بعدی", Toast.LENGTH_SHORT).show()
                    else -> typeText("😊")
                }
            }, LinearLayout.LayoutParams(0, -1, if(label == "فاصله") 3f else 1f))
        }
        outer.addView(bottom, LinearLayout.LayoutParams(-1, 58))
        return outer
    }

    private fun key(label: String, dark: Boolean, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if(label.length > 5) 12f else 18f
            setTextColor(if(dark) Color.WHITE else Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = 0; minimumWidth = 0
            minHeight = 0; minimumHeight = 0
            setPadding(0,0,0,0)
            background = GradientDrawable().apply {
                setColor(if(dark) Color.rgb(28,65,111) else Color.rgb(247,249,252))
                cornerRadius = 7f
                setStroke(1, Color.rgb(105,116,130))
            }
            setOnClickListener { action() }
        }

    private fun typeText(s: String) {
        currentInputConnection?.let {
            undoStack.add(s); redoStack.clear(); it.commitText(s, 1)
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
}

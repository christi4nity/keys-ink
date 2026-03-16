package com.keysink.inputmethod.latin.suggestions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.keysink.inputmethod.R
import com.keysink.inputmethod.latin.SuggestedWords

class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {

    interface Listener {
        fun pickSuggestionManually(wordInfo: SuggestedWords.SuggestedWordInfo)
    }

    private var listener: Listener? = null
    private var suggestedWords: SuggestedWords = SuggestedWords.EMPTY

    private lateinit var leftView: TextView
    private lateinit var centerView: TextView
    private lateinit var rightView: TextView

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        leftView = findViewById(R.id.suggestion_left)
        centerView = findViewById(R.id.suggestion_center)
        rightView = findViewById(R.id.suggestion_right)

        leftView.setOnClickListener(this)
        centerView.setOnClickListener(this)
        rightView.setOnClickListener(this)
    }

    fun setSuggestions(suggestedWords: SuggestedWords) {
        this.suggestedWords = suggestedWords
        val count = suggestedWords.size()

        // Center: best suggestion if available, otherwise typed word
        // Left: typed word (when suggestions exist)
        // Right: 2nd suggestion
        if (count > 1) {
            centerView.text = suggestedWords.getWord(1)
            leftView.text = suggestedWords.getWord(0)
            rightView.text = if (count > 2) suggestedWords.getWord(2) else ""
        } else {
            centerView.text = if (count > 0) suggestedWords.getWord(0) else ""
            leftView.text = ""
            rightView.text = ""
        }
    }

    fun clear() {
        leftView.text = ""
        centerView.text = ""
        rightView.text = ""
        suggestedWords = SuggestedWords.EMPTY
    }

    override fun onClick(v: View) {
        val index = when (v.id) {
            R.id.suggestion_center -> if (suggestedWords.size() > 1) 1 else 0
            R.id.suggestion_left -> 0
            R.id.suggestion_right -> 2
            else -> return
        }
        if (index < suggestedWords.size()) {
            listener?.pickSuggestionManually(suggestedWords.getInfo(index))
        }
    }
}

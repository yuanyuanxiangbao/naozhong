package com.taskalarm

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import kotlin.random.Random

class MathTaskView(
    context: Context,
    private val total: Int,
    private val hard: Boolean,
    private val onSolved: () -> Unit,
) : LinearLayout(context) {

    private var solved = 0
    private var answer = ""
    private val questionView = TextView(context)
    private val input = EditText(context)
    private val progressView = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 12, 0, 12)

        val title = TextView(context).apply {
            text = "计算并输入答案"
            setTextColor(color(R.color.ring_text))
            textSize = 18f
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }

        progressView.apply {
            setTextColor(color(R.color.ring_text_dim))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }

        questionView.apply {
            setTextColor(color(R.color.ring_text))
            textSize = 42f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 22)
        }

        input.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(color(R.color.text_primary))
            textSize = 26f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_ring_input)
            setPadding(28, 18, 28, 18)
        }

        addView(title)
        addView(progressView)
        addView(questionView)
        addView(input)

        input.doAfterTextChanged { text ->
            if (answer.isNotEmpty() && text?.toString() == answer) {
                solved++
                input.setText("")
                if (solved >= total) {
                    progressView.text = "已完成 $total / $total"
                    onSolved()
                } else {
                    nextProblem()
                }
            }
        }

        nextProblem()
    }

    private fun nextProblem() {
        val a: Int
        val b: Int
        val op: String
        if (hard) {
            // 困难模式固定两位数答案，避免用户误以为只能输入一位
            a = Random.nextInt(4, 13)
            b = Random.nextInt(4, 10)
            op = "×"
            answer = (a * b).toString()
        } else {
            val first = Random.nextInt(1, 21)
            val second = Random.nextInt(1, 21)
            if (Random.nextBoolean()) {
                a = first
                b = second
                op = "+"
                answer = (first + second).toString()
            } else {
                a = maxOf(first, second)
                b = minOf(first, second)
                op = "−"
                answer = (a - b).toString()
            }
        }
        progressView.text = "已完成 $solved / $total"
        questionView.text = "$a $op $b = ?"
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)
}

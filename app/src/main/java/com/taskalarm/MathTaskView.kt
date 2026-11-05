package com.taskalarm

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
            setTextColor(Color.parseColor("#F5F3FF"))
            textSize = 20f
            gravity = Gravity.CENTER
        }

        progressView.apply {
            setTextColor(Color.parseColor("#C9C4F2"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }

        questionView.apply {
            setTextColor(Color.parseColor("#F5F3FF"))
            textSize = 36f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }

        input.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(Color.parseColor("#1B1B1F"))
            textSize = 28f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_card)
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
}

package com.taskalarm

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Android 15 起窗口强制延伸到系统栏下方，手动给内容补回状态栏和导航栏间距
internal fun View.applySystemBarsPadding() {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            left + bars.left,
            top + bars.top,
            right + bars.right,
            bottom + bars.bottom
        )
        insets
    }
}

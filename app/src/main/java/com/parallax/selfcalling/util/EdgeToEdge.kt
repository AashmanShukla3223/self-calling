package com.parallax.selfcalling.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Android 15+ (API 35) enforces edge-to-edge rendering for apps targeting SDK 35+, so
 * content draws behind the status and navigation bars. This pads the window content with
 * the system-bar insets. On Android 6–14 (API 23–34) it is a no-op — those versions keep
 * content below the bars by default, which is the authentic 2015 look.
 */
object EdgeToEdge {

    @JvmStatic
    fun apply(a: Activity) {
        if (Build.VERSION.SDK_INT < 35) return
        val root = a.findViewById<View>(android.R.id.content)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.requestApplyInsets()
    }
}

package com.trozovka.wxlite

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

/**
 * Skeleton entry point — proves the build/packaging toolchain works.
 * Real weatherfax rendering, map, and forecast UI come next; this just
 * needs to launch and stay running.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            text = "Trozovka WX Lite\n(skeleton build)"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(label)
    }
}

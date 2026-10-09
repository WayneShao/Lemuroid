package com.swordfish.lemuroid.app.rayneo

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.util.WeakHashMap

/** Visible feedback belongs to the active app window, including Dialog and game windows. */
object RayNeoMessages {
    private val overlays = WeakHashMap<ViewGroup, View>()

    fun show(
        activity: Activity,
        message: String,
        duration: Int,
    ): Boolean {
        val root =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() } as? ViewGroup
            } else {
                activity.window.decorView as? ViewGroup
            } ?: return false
        if (!root.isAttachedToWindow || !root.hasWindowFocus() || root.width <= 0) return false

        overlays.remove(root)?.let(root::removeView)
        val width = root.width / 2
        // Two lightweight labels ensure dynamic messages invalidate both eyes, without bitmap capture.
        val overlay = FrameLayout(activity)
        repeat(2) { eye ->
            val label =
                TextView(activity).apply {
                    text = message
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(16, 10, 16, 10)
                    background =
                        GradientDrawable().apply {
                            setColor(0xEE202020.toInt())
                            cornerRadius = 12f
                        }
                    importantForAccessibility =
                        if (eye == 0) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            overlay.addView(
                label,
                FrameLayout.LayoutParams(width - 64, -2, Gravity.BOTTOM).apply {
                    leftMargin = eye * width + 32
                    bottomMargin = 48
                },
            )
        }
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlays[root] = overlay
        val hide =
            Runnable {
                if (overlays[root] === overlay) {
                    overlays.remove(root)
                    root.removeView(overlay)
                }
            }
        overlay.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {}

                override fun onViewDetachedFromWindow(view: View) {
                    view.removeCallbacks(hide)
                    if (overlays[root] === overlay) overlays.remove(root)
                }
            },
        )
        overlay.postDelayed(hide, if (duration == Toast.LENGTH_LONG) 3500L else 2000L)
        return true
    }
}

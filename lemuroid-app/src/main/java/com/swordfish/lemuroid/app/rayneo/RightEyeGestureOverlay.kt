package com.swordfish.lemuroid.app.rayneo

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import timber.log.Timber

/**
 * Invisible view positioned over the right-eye region (x=640..1280) on the
 * RayNeo X3 Pro. Its sole job is to receive Mercury mirror-lens events and
 * translate a right-lens double-tap into an Android back press.
 *
 * Why this is needed: declaring `com.rayneo.mercury.app=true` in the manifest
 * tells Mercury to forward raw cyttsp events to the app instead of handling
 * them as system gestures. Without this overlay, those events land nowhere and
 * the system double-tap exit stops working.
 *
 * Placement: added by [DualEyeActivityWrapper] as the topmost child of
 * android.R.id.content with leftMargin=640, so FrameLayout dispatch hits it
 * first for any event in the right-eye region.
 */
class RightEyeGestureOverlay(
    context: Context,
    private val onDoubleTap: () -> Unit = { (context as? Activity)?.onBackPressed() },
) : View(context) {
    private var lastTapMs = 0L
    private val doubleTapWindowMs = 600L // generous window — mirror lens taps are slower than touchscreen

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return handleEvent(ev)
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        // Mercury sometimes delivers cyttsp events via the generic motion path
        return handleEvent(ev)
    }

    private fun handleEvent(ev: MotionEvent): Boolean {
        Timber.v("RightEyeGestureOverlay: action=${ev.actionMasked} source=0x${ev.source.toString(16)}")
        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_BUTTON_PRESS,
            -> true // claim the gesture stream

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_BUTTON_RELEASE,
            -> {
                val now = System.currentTimeMillis()
                val delta = now - lastTapMs
                Timber.d("RightEyeGestureOverlay: UP delta=${delta}ms")
                if (lastTapMs != 0L && delta <= doubleTapWindowMs) {
                    Timber.d("RightEyeGestureOverlay: double-tap → back press")
                    lastTapMs = 0L
                    onDoubleTap()
                } else {
                    lastTapMs = now
                }
                true
            }

            MotionEvent.ACTION_MOVE -> true

            else -> false
        }
    }
}

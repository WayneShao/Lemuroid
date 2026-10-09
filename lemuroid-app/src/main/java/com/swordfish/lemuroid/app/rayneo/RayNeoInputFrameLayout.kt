package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** Observe actual temple devices before hit-testing; ordinary pointer events keep their original path. */
class RayNeoInputFrameLayout(context: Context) : FrameLayout(context) {
    var templeInput: RightEyeGestureOverlay? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        templeInput?.dispatchTempleEvent(event) == true || super.dispatchTouchEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        templeInput?.dispatchTempleEvent(event) == true || super.dispatchGenericMotionEvent(event)
}

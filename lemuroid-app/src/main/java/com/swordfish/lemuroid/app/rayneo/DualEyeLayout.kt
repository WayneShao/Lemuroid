package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout

/**
 * A FrameLayout that renders its single child (sized at [SINGLE_EYE_WIDTH] px)
 * twice on the hardware canvas — once at x=0 (left eye) and once at
 * x=[SINGLE_EYE_WIDTH] (right eye) — producing a side-by-side stereo image
 * on the RayNeo X3 Pro's 1280×480 display.
 *
 * In hardware-accelerated mode Android records child draw operations into
 * RenderNodes (GPU display lists). The second child draw simply
 * replays those already-recorded display lists; no re-execution of draw code
 * and no CPU bitmap readback is needed. This keeps a single View hierarchy
 * and a single set of UI state; independent windows still need their own wrapper.
 *
 * Does NOT work for SurfaceView / GLSurfaceView children — those bypass the
 * View canvas system entirely. The game screen (GLRetroView) is handled
 * separately via RayNeoGameActivity + RayNeoGameScreen + RayNeoMirrorView.
 */
class DualEyeLayout(
    context: Context,
    private val eyeWidth: Int = SINGLE_EYE_WIDTH,
) : FrameLayout(context) {
    /**
     * Mercury sends mirror-lens gestures as mouse/pointer events at x ≥ 640
     * (the right-eye region). Since our content is constrained to 640 px,
     * ViewRootImpl would cancel those events as "out of bounds".
     * Remap them to the equivalent left-eye coordinate so they reach the child.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return dispatchToLeftEye(ev) { super.dispatchTouchEvent(it) }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return dispatchToLeftEye(ev) { super.dispatchGenericMotionEvent(it) }
    }

    private inline fun dispatchToLeftEye(
        ev: MotionEvent,
        dispatch: (MotionEvent) -> Boolean,
    ): Boolean {
        if (ev.x < eyeWidth) return dispatch(ev)
        val mappedEvent = MotionEvent.obtain(ev)
        try {
            mappedEvent.offsetLocation(-eyeWidth.toFloat(), 0f)
            return dispatch(mappedEvent)
        } finally {
            mappedEvent.recycle()
        }
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        // Repeat each child's drawing within one traversal, including dynamically invalidated content.
        val left = super.drawChild(canvas, child, drawingTime)
        val checkpoint = canvas.save()
        return try {
            canvas.translate(eyeWidth.toFloat(), 0f)
            super.drawChild(canvas, child, drawingTime) || left
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    override fun onDescendantInvalidated(
        child: View,
        target: View,
    ) {
        super.onDescendantInvalidated(child, target)
        // The dirty child lives only in the left half, but both output regions need repainting.
        invalidate()
    }

    @Suppress("DEPRECATION")
    override fun invalidateChildInParent(
        location: IntArray,
        dirty: Rect,
    ): ViewParent? {
        // Native pickers use a software canvas. Their dirty region must include both eyes too.
        dirty.set(-location[0], -location[1], width - location[0], height - location[1])
        return super.invalidateChildInParent(location, dirty)
    }

    companion object {
        /** Physical pixel width of one eye on the RayNeo X3 Pro (1280px / 2). */
        const val SINGLE_EYE_WIDTH = 640
    }
}

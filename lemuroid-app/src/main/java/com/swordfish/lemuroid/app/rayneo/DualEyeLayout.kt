package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * A FrameLayout that renders its single child (sized at [SINGLE_EYE_WIDTH] px)
 * twice on the hardware canvas — once at x=0 (left eye) and once at
 * x=[SINGLE_EYE_WIDTH] (right eye) — producing a side-by-side stereo image
 * on the RayNeo X3 Pro's 1280×480 display.
 *
 * In hardware-accelerated mode Android records child draw operations into
 * RenderNodes (GPU display lists). The second [super.dispatchDraw] call simply
 * replays those already-recorded display lists; no re-execution of draw code
 * and no CPU readback are needed. This is the highest-performance way to
 * duplicate View-hierarchy content on Android.
 *
 * Does NOT work for SurfaceView / GLSurfaceView children — those bypass the
 * View canvas system entirely. The game screen (GLRetroView) is handled
 * separately via RayNeoGameActivity + RayNeoGameScreen + RayNeoMirrorView.
 */
class DualEyeLayout(context: Context) : FrameLayout(context) {

    /**
     * Mercury sends mirror-lens gestures as mouse/pointer events at x ≥ 640
     * (the right-eye region). Since our content is constrained to 640 px,
     * ViewRootImpl would cancel those events as "out of bounds".
     * Remap them to the equivalent left-eye coordinate so they reach the child.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        remapToLeftEyeIfNeeded(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        remapToLeftEyeIfNeeded(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun remapToLeftEyeIfNeeded(ev: MotionEvent) {
        if (ev.x >= SINGLE_EYE_WIDTH) {
            ev.offsetLocation(-SINGLE_EYE_WIDTH.toFloat(), 0f)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Left eye — normal render at x=0
        super.dispatchDraw(canvas)

        // Right eye — translate and replay the same RenderNode display lists
        canvas.save()
        canvas.translate(SINGLE_EYE_WIDTH.toFloat(), 0f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    companion object {
        /** Physical pixel width of one eye on the RayNeo X3 Pro (1280px / 2). */
        const val SINGLE_EYE_WIDTH = 640
    }
}

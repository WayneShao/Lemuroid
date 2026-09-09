package com.swordfish.lemuroid.app.rayneo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.FrameLayout
import timber.log.Timber

/**
 * ActivityLifecycleCallbacks that wraps every Activity's root content view
 * with a [DualEyeLayout] so the entire app appears in dual-eye mode on
 * the RayNeo X3 Pro without touching any individual screen.
 *
 * [RayNeoGameActivity] is excluded — it renders GLRetroView directly and
 * manages its own right-eye mirror via RayNeoMirrorView + PixelCopy.
 */
class DualEyeActivityWrapper : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        activity.window.decorView.post {
            if (activity is RayNeoGameActivity) {
                // Game screen manages its own dual-eye rendering (GLRetroView + PixelCopy).
                // Only add the right-eye gesture overlay so double-tap exit still works.
                addRightEyeOverlayOnly(activity)
            } else {
                wrapWithDualEye(activity)
            }
        }
    }

    private fun addRightEyeOverlayOnly(activity: Activity) {
        val contentFrame =
            activity.window.decorView
                .findViewById<FrameLayout>(android.R.id.content) ?: return
        // Avoid duplicating the overlay on config changes
        if (contentFrame.findViewWithTag<RightEyeGestureOverlay>("right_eye_overlay") != null) return
        val rightEyeOverlay = RightEyeGestureOverlay(activity).apply { tag = "right_eye_overlay" }
        contentFrame.addView(
            rightEyeOverlay,
            FrameLayout.LayoutParams(DualEyeLayout.SINGLE_EYE_WIDTH, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                leftMargin = DualEyeLayout.SINGLE_EYE_WIDTH
            },
        )
        Timber.d("DualEyeActivityWrapper: added right-eye overlay to ${activity.javaClass.simpleName}")
    }

    private fun wrapWithDualEye(activity: Activity) {
        val contentFrame =
            activity.window.decorView
                .findViewById<FrameLayout>(android.R.id.content) ?: return

        if (contentFrame.childCount == 0) return

        val child = contentFrame.getChildAt(0)
        if (child is DualEyeLayout) return // already wrapped (e.g. after config change)

        Timber.d("DualEyeActivityWrapper: wrapping ${activity.javaClass.simpleName}")

        contentFrame.removeViewAt(0)

        val dualEye = DualEyeLayout(activity)
        dualEye.addView(
            child,
            FrameLayout.LayoutParams(DualEyeLayout.SINGLE_EYE_WIDTH, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        contentFrame.addView(
            dualEye,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        // Transparent overlay over the right-eye region (x=640..1280) to catch
        // Mercury mirror-lens events and translate double-tap → back press.
        val rightEyeOverlay = RightEyeGestureOverlay(activity)
        contentFrame.addView(
            rightEyeOverlay,
            FrameLayout.LayoutParams(DualEyeLayout.SINGLE_EYE_WIDTH, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                leftMargin = DualEyeLayout.SINGLE_EYE_WIDTH
            },
        )
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {}

    override fun onActivityDestroyed(activity: Activity) {}
}

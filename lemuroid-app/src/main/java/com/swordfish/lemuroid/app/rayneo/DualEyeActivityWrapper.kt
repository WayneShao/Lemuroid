package com.swordfish.lemuroid.app.rayneo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import timber.log.Timber
import java.util.WeakHashMap

/**
 * ActivityLifecycleCallbacks that wraps every Activity's root content view
 * with a [DualEyeLayout] so the entire app appears in dual-eye mode on
 * the RayNeo X3 Pro without touching any individual screen.
 *
 * [RayNeoGameActivity] is excluded — it renders GLRetroView directly and
 * manages its own right-eye mirror via RayNeoMirrorView + PixelCopy.
 */
class DualEyeActivityWrapper : Application.ActivityLifecycleCallbacks {
    private val listeners = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val listener = ViewTreeObserver.OnGlobalLayoutListener { install(activity, content) }
        listeners[activity] = listener
        content.viewTreeObserver.addOnGlobalLayoutListener(listener)
        content.post {
            if (!activity.isDestroyed && !activity.isFinishing) install(activity, content)
        }
    }

    private fun install(
        activity: Activity,
        content: FrameLayout,
    ) {
        if (activity.isDestroyed || content.childCount == 0) return
        val existing = (0 until content.childCount).map { content.getChildAt(it) }
        var host = existing.filterIsInstance<RayNeoInputFrameLayout>().firstOrNull()
        val additions = existing.filter { it !== host }
        if (host != null && additions.isEmpty()) return
        if (host == null) {
            host = RayNeoInputFrameLayout(activity)
            val body = if (activity is RayNeoGameActivity) FrameLayout(activity) else DualEyeLayout(activity)
            body.tag = CONTENT_TAG
            host.addView(body, FrameLayout.LayoutParams(-1, -1))
            val input = RightEyeGestureOverlay(activity)
            input.setKeyDispatcher(activity::dispatchKeyEvent)
            host.templeInput = input
            host.addView(
                input,
                FrameLayout.LayoutParams(DualEyeLayout.SINGLE_EYE_WIDTH, -1).apply {
                    leftMargin = DualEyeLayout.SINGLE_EYE_WIDTH
                },
            )
            content.addView(host, FrameLayout.LayoutParams(-1, -1))
        }
        val body = host.findViewWithTag<FrameLayout>(CONTENT_TAG)
        host.templeInput?.cancelPendingInput()
        additions.forEach { child ->
            val old = child.layoutParams
            prepareComposition(child)
            content.removeView(child)
            val params = FrameLayout.LayoutParams(old)
            if (activity !is RayNeoGameActivity && params.width != 0) {
                params.width = DualEyeLayout.SINGLE_EYE_WIDTH
            }
            body.addView(child, params)
        }
        Timber.d("RayNeo content attached: %s, roots=%d", activity.javaClass.simpleName, additions.size)
    }

    private fun prepareComposition(view: View) {
        if (view is AbstractComposeView) {
            // Normally installation happens before attach in onActivityStarted. A genuinely late
            // root may already own a window recomposer which detaching will cancel; recreate only
            // that new root's composition instead of retaining a frozen one in the stereo host.
            if (view.isAttachedToWindow) view.disposeComposition()
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) prepareComposition(view.getChildAt(index))
        }
    }

    override fun onActivityStarted(activity: Activity) {
        // setContent() has run, but the window has not attached yet. Reparenting after the first
        // layout detaches the Compose root and cancels its window recomposer, freezing all UI updates.
        activity.findViewById<FrameLayout>(android.R.id.content)?.let { install(activity, it) }
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {}

    override fun onActivityDestroyed(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content)
        listeners.remove(activity)?.let { content?.viewTreeObserver?.removeOnGlobalLayoutListener(it) }
    }

    companion object {
        private const val CONTENT_TAG = "rayneo_activity_content"
    }
}

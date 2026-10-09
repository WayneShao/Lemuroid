package com.swordfish.lemuroid.app.rayneo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import timber.log.Timber

/** Transparent input adapter with per-window gesture/focus state, also usable before child dispatch. */
class RightEyeGestureOverlay(
    context: Context,
    private val onDoubleTap: () -> Unit = { context.findActivity()?.onBackPressed() },
) : View(context) {
    private val navigation = RayNeoWindowNavigation(this)
    private val recognizer =
        RayNeoGestureRecognizer(
            tapSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            swipeDistance = 32f * resources.displayMetrics.density,
        )
    private val confirmPendingTap = Runnable { execute(recognizer.advanceTime(SystemClock.uptimeMillis())) }
    private var gestureTarget: RayNeoWindowNavigation.Target? = null

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Activity adapters bind their own callback; dialogs retain dispatch through their own root. */
    fun setKeyDispatcher(dispatcher: (KeyEvent) -> Boolean) {
        navigation.setKeyDispatcher(dispatcher)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = dispatchTempleEvent(event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean = dispatchTempleEvent(event)

    /** System sensors stop before child controls; external mouse/gamepad input retains its original path. */
    fun dispatchTempleEvent(event: MotionEvent): Boolean {
        val deviceName = event.device?.name
        if (RayNeoTempleInput.rejectBeforeChildren(deviceName)) return true
        if (!RayNeoTempleInput.isRightTemple(deviceName)) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
            Timber.tag(
                "RayNeoInput",
            ).d(
                "device=%s action=%d x=%.1f y=%.1f active=%s",
                deviceName,
                event.actionMasked,
                event.x,
                event.y,
                navigation.isActive(),
            )
        }
        if (!navigation.isActive()) {
            cancelPendingInput()
            return false
        }
        val action =
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> RayNeoGestureRecognizer.Action.DOWN
                MotionEvent.ACTION_MOVE -> RayNeoGestureRecognizer.Action.MOVE
                MotionEvent.ACTION_UP -> RayNeoGestureRecognizer.Action.UP
                MotionEvent.ACTION_POINTER_DOWN -> RayNeoGestureRecognizer.Action.POINTER_DOWN
                MotionEvent.ACTION_POINTER_UP -> RayNeoGestureRecognizer.Action.POINTER_UP
                MotionEvent.ACTION_CANCEL -> RayNeoGestureRecognizer.Action.CANCEL
                MotionEvent.ACTION_BUTTON_PRESS -> RayNeoGestureRecognizer.Action.BUTTON_PRESS
                MotionEvent.ACTION_BUTTON_RELEASE -> RayNeoGestureRecognizer.Action.BUTTON_RELEASE
                else -> return false
            }
        removeCallbacks(confirmPendingTap)
        if (
            action == RayNeoGestureRecognizer.Action.DOWN || action == RayNeoGestureRecognizer.Action.BUTTON_PRESS ||
            action == RayNeoGestureRecognizer.Action.POINTER_DOWN || action == RayNeoGestureRecognizer.Action.CANCEL ||
            event.flags and FLAG_CANCELED != 0
        ) {
            // This also cancels a CENTER waiting for layout after its double-tap timeout elapsed.
            navigation.cancel()
        }
        val target =
            if (action == RayNeoGestureRecognizer.Action.DOWN ||
                action == RayNeoGestureRecognizer.Action.BUTTON_PRESS
            ) {
                navigation.captureTarget().also { gestureTarget = it }
            } else {
                if (gestureTarget != null && gestureTarget != navigation.currentTarget()) {
                    cancelPendingInput()
                    return true
                }
                null
            }
        // Keep the coordinates delivered by InputReader; firmware has already applied direction settings.
        val pointers =
            (0 until event.pointerCount).map {
                RayNeoGestureRecognizer.Pointer(event.getPointerId(it), event.getX(it), event.getY(it))
            }
        val canceled = event.flags and FLAG_CANCELED != 0
        // Android may batch motion samples; an excursion must remain a move even if it returned.
        if (!canceled && action == RayNeoGestureRecognizer.Action.MOVE) {
            for (history in 0 until event.historySize) {
                val historicalPointers =
                    (0 until event.pointerCount).map {
                        RayNeoGestureRecognizer.Pointer(
                            event.getPointerId(it),
                            event.getHistoricalX(it, history),
                            event.getHistoricalY(it, history),
                        )
                    }
                recognizer.onEvent(
                    RayNeoGestureRecognizer.Event(
                        action,
                        event.getHistoricalEventTime(history),
                        event.downTime,
                        historicalPointers,
                        historicalPointers.firstOrNull()?.id ?: -1,
                    ),
                    null,
                )
            }
        }
        val input =
            RayNeoGestureRecognizer.Event(
                action,
                event.eventTime,
                event.downTime,
                pointers,
                pointers.getOrNull(event.actionIndex)?.id ?: -1,
                canceled,
            )
        execute(recognizer.onEvent(input, target))
        recognizer.nextTapAt?.let { postDelayed(confirmPendingTap, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) navigation.onWindowReady() else cancelPendingInput()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { navigation.onWindowReady() }
    }

    override fun onDetachedFromWindow() {
        cancelPendingInput()
        super.onDetachedFromWindow()
    }

    fun cancelPendingInput() {
        removeCallbacks(confirmPendingTap)
        gestureTarget = null
        recognizer.cancel()
        navigation.cancel()
    }

    private fun execute(commands: List<RayNeoGestureRecognizer.Command>) {
        if (!navigation.isActive()) {
            cancelPendingInput()
            return
        }
        commands.forEach { command ->
            Timber.tag("RayNeoInput").d("gesture=%s focused=%s", command.gesture, navigation.currentTarget() != null)
            if (command.gesture == RayNeoGestureRecognizer.Gesture.BACK) {
                cancelPendingInput()
                onDoubleTap()
            } else {
                navigation.execute(command)
            }
        }
    }

    private companion object {
        // Added as MotionEvent.FLAG_CANCELED in API 33; the bit is readable on older firmware too.
        const val FLAG_CANCELED = 0x20
    }
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.takeUnless { it === this }?.findActivity()
        else -> null
    }

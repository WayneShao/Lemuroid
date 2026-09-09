package com.swordfish.lemuroid.app.rayneo

import android.view.MotionEvent
import timber.log.Timber

/**
 * Minimal mirror-lens gesture recogniser for RayNeo X3 Pro.
 *
 * Strategy (Playbook §8):
 *   - Detect events whose InputDevice name contains "cyttsp" or "capsense".
 *   - Left lens → pass through to system (don't consume).
 *   - Right lens → handle double-tap only (conservative starter set).
 *
 * Device-side assignment heuristic: first cyttsp device seen = right lens,
 * second distinct deviceId = left lens. Validate with logcat on real hardware
 * and update if needed.
 *
 * Note: Mercury may intercept cyttsp5_mt events before they reach the app.
 * If so, these callbacks will simply never fire — which is safe.
 */
class RayNeoTempleInput(
    private val onRightDoubleTap: () -> Unit = {},
) {
    private var rightDeviceId: Int? = null
    private var leftDeviceId: Int? = null

    // Double-tap detection state (right lens only)
    private var lastTapTimeMs: Long = 0L
    private val doubleTapWindowMs = 400L

    fun onTouchEvent(ev: MotionEvent): Boolean {
        val device = ev.device ?: return false
        if (!isTempleDevice(device.name)) return false

        assignDeviceIfNeeded(ev.deviceId)
        if (ev.deviceId == leftDeviceId) return false // let system handle left lens

        return handleRightLens(ev)
    }

    fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        val device = ev.device ?: return false
        if (!isTempleDevice(device.name)) return false

        assignDeviceIfNeeded(ev.deviceId)
        if (ev.deviceId == leftDeviceId) return false

        return handleRightLens(ev)
    }

    private fun isTempleDevice(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("cyttsp") || lower.contains("capsense")
    }

    private fun assignDeviceIfNeeded(deviceId: Int) {
        when {
            rightDeviceId == null -> {
                rightDeviceId = deviceId
                Timber.d("RayNeoTempleInput: right lens deviceId=$deviceId")
            }
            leftDeviceId == null && deviceId != rightDeviceId -> {
                leftDeviceId = deviceId
                Timber.d("RayNeoTempleInput: left lens deviceId=$deviceId")
            }
        }
    }

    private fun handleRightLens(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            val now = System.currentTimeMillis()
            if (now - lastTapTimeMs <= doubleTapWindowMs) {
                Timber.d("RayNeoTempleInput: right double-tap")
                onRightDoubleTap()
                lastTapTimeMs = 0L
            } else {
                lastTapTimeMs = now
            }
            return true
        }
        // Consume DOWN/MOVE on the right lens to avoid accidental game input.
        return ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE
    }
}

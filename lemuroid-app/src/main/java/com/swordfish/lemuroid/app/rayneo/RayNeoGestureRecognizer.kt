package com.swordfish.lemuroid.app.rayneo

import kotlin.math.abs
import kotlin.math.max

/** Event-time state machine; Android scheduling and window ownership live in the adapter. */
internal class RayNeoGestureRecognizer(
    private val tapSlop: Float,
    private val swipeDistance: Float,
    private val doubleTapMillis: Long = 320,
    private val tapMillis: Long = 500,
) {
    enum class Action { DOWN, MOVE, UP, POINTER_DOWN, POINTER_UP, CANCEL, BUTTON_PRESS, BUTTON_RELEASE }

    enum class Gesture { UP, DOWN, LEFT, RIGHT, CONFIRM, BACK, MENU }

    data class Pointer(val id: Int, val x: Float, val y: Float)

    data class Event(
        val action: Action,
        val time: Long,
        val downTime: Long,
        val pointers: List<Pointer>,
        val changedId: Int,
        val canceled: Boolean = false,
    )

    data class Command(val gesture: Gesture, val target: Any? = null)

    private data class Tap(val time: Long, val target: Any?)

    private class Stroke(val downTime: Long, val time: Long, val target: Any?, first: Pointer) {
        val origins = mutableMapOf(first.id to first)
        val activeIds = mutableSetOf(first.id)
        var multiple = false
        var rejected = false
        var moved = false
        var dx = 0f
        var dy = 0f
    }

    private var stroke: Stroke? = null
    private var pendingTap: Tap? = null
    private var completedDownTime: Long? = null
    private var completedTime = Long.MIN_VALUE
    private var lastEvent: Event? = null

    val nextTapAt: Long? get() = if (stroke == null) pendingTap?.time?.plus(doubleTapMillis) else null

    fun onEvent(
        event: Event,
        target: Any?,
    ): List<Command> {
        if (event == lastEvent) return emptyList()
        lastEvent = event
        if (event.action == Action.CANCEL || event.canceled) {
            pendingTap = null
            stroke?.rejected = true
            if (event.action == Action.CANCEL || event.action == Action.UP) {
                stroke = null
                completedDownTime = event.downTime
                completedTime = event.time
            }
            return emptyList()
        }
        if (event.action == Action.DOWN || event.action == Action.BUTTON_PRESS) {
            if (stroke != null || (completedDownTime == event.downTime && event.time <= completedTime)) {
                return emptyList()
            }
            val expired = advanceTime(event.time)
            val first = event.pointers.singleOrNull() ?: return expired
            stroke = Stroke(event.downTime, event.time, target, first)
            return expired
        }
        val current = stroke ?: return emptyList()
        if (event.downTime != current.downTime) return emptyList()
        updateMovement(current, event.pointers)
        when (event.action) {
            Action.POINTER_DOWN -> {
                pendingTap = null
                current.multiple = true
                val pointer = event.pointers.firstOrNull { it.id == event.changedId }
                if (current.origins.size != 1 || pointer == null || pointer.id in current.origins || current.moved) {
                    current.rejected = true
                } else {
                    current.origins[pointer.id] = pointer
                    current.activeIds += pointer.id
                }
            }
            Action.POINTER_UP -> {
                if (!current.multiple || !current.activeIds.remove(event.changedId)) current.rejected = true
            }
            Action.UP, Action.BUTTON_RELEASE -> {
                if (event.pointers.size != 1 || current.activeIds.singleOrNull() != event.changedId) {
                    current.rejected = true
                }
                stroke = null
                completedDownTime = current.downTime
                completedTime = event.time
                return finish(current, event.time)
            }
            else -> Unit
        }
        if (event.pointers.size > 2) current.rejected = true
        return emptyList()
    }

    private fun updateMovement(
        current: Stroke,
        pointers: List<Pointer>,
    ) {
        for (pointer in pointers) {
            val origin = current.origins[pointer.id] ?: continue
            val dx = pointer.x - origin.x
            val dy = pointer.y - origin.y
            val distance = max(abs(dx), abs(dy))
            if (distance > tapSlop) {
                current.moved = true
                pendingTap = null
                if (current.multiple) current.rejected = true
            }
            if (distance > max(abs(current.dx), abs(current.dy))) {
                current.dx = dx
                current.dy = dy
            }
        }
    }

    private fun finish(
        current: Stroke,
        time: Long,
    ): List<Command> {
        if (current.rejected || time < current.time) {
            pendingTap = null
            return emptyList()
        }
        if (current.multiple) {
            pendingTap = null
            return if (current.moved || time - current.time > tapMillis) emptyList() else listOf(Command(Gesture.MENU))
        }
        if (current.moved) {
            pendingTap = null
            if (max(abs(current.dx), abs(current.dy)) < swipeDistance) return emptyList()
            val direction =
                if (abs(current.dx) >= abs(current.dy)) {
                    if (current.dx > 0) Gesture.RIGHT else Gesture.LEFT
                } else {
                    if (current.dy > 0) Gesture.DOWN else Gesture.UP
                }
            return listOf(Command(direction))
        }
        if (time - current.time > tapMillis) {
            pendingTap = null
            return emptyList()
        }
        val previous = pendingTap
        if (previous != null && previous.target == current.target &&
            current.time - previous.time in 0..doubleTapMillis
        ) {
            pendingTap = null
            return listOf(Command(Gesture.BACK))
        }
        pendingTap = Tap(time, current.target)
        return emptyList()
    }

    fun advanceTime(time: Long): List<Command> {
        val pending = pendingTap ?: return emptyList()
        if (stroke != null || time < pending.time + doubleTapMillis) return emptyList()
        pendingTap = null
        return if (pending.target == null) emptyList() else listOf(Command(Gesture.CONFIRM, pending.target))
    }

    fun cancel() {
        stroke = null
        pendingTap = null
        completedDownTime = null
        completedTime = Long.MIN_VALUE
        lastEvent = null
    }
}

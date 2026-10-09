package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNeoGestureRecognizerTest {
    private val recognizer = RayNeoGestureRecognizer(tapSlop = 10f, swipeDistance = 25f, doubleTapMillis = 300)
    private val commands = mutableListOf<RayNeoGestureRecognizer.Command>()
    private var downTime = 100L

    @Test
    fun singleTapWaitsAndKeepsOriginalTarget() {
        event(Action.DOWN, 100, point(7), target = "original")
        event(Action.UP, 130, point(7), target = "replacement")
        commands += recognizer.advanceTime(429)
        assertTrue(commands.isEmpty())
        commands += recognizer.advanceTime(430)
        assertEquals(listOf(Command(Gesture.CONFIRM, "original")), commands)
        assertTrue(recognizer.advanceTime(900).isEmpty())
    }

    @Test
    fun doubleTapCancelsFirstConfirmAndEmitsBackOnlyOnce() {
        tap(100)
        tap(250)
        event(Action.BUTTON_RELEASE, 280, point(7))
        commands += recognizer.advanceTime(1000)
        assertEquals(listOf(Command(Gesture.BACK)), commands)
    }

    @Test
    fun touchAndGenericDuplicatesDoNotCreateDoubleTap() {
        event(Action.DOWN, 100, point(7))
        event(Action.BUTTON_PRESS, 100, point(7))
        event(Action.UP, 130, point(7))
        event(Action.BUTTON_RELEASE, 130, point(7))
        event(Action.UP, 130, point(7))
        commands += recognizer.advanceTime(430)
        assertEquals(listOf(Command(Gesture.CONFIRM, "target")), commands)
    }

    @Test
    fun buttonOnlySequenceSupportsSingleTap() {
        event(Action.BUTTON_PRESS, 100, point(7))
        event(Action.BUTTON_RELEASE, 130, point(7))
        commands += recognizer.advanceTime(430)
        assertEquals(listOf(Command(Gesture.CONFIRM, "target")), commands)
    }

    @Test
    fun buttonOnlyChannelCanReuseZeroDownTimeForLaterPresses() {
        downTime = 0
        event(Action.BUTTON_PRESS, 100, point(7))
        event(Action.BUTTON_RELEASE, 130, point(7))
        event(Action.BUTTON_PRESS, 250, point(7))
        event(Action.BUTTON_RELEASE, 280, point(7))
        assertEquals(listOf(Command(Gesture.BACK)), commands)
    }

    @Test
    fun lateDuplicateDownAfterCancelCannotResurrectCanceledTouch() {
        event(Action.DOWN, 100, point(7))
        event(Action.CANCEL, 140, point(7))
        event(Action.DOWN, 100, point(7))
        event(Action.UP, 160, point(7))
        commands += recognizer.advanceTime(1000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun swipesPreserveSystemCoordinateSigns() {
        listOf(
            Triple(40f, 3f, Gesture.RIGHT),
            Triple(-40f, 3f, Gesture.LEFT),
            Triple(3f, 40f, Gesture.DOWN),
            Triple(3f, -40f, Gesture.UP),
        ).forEachIndexed { index, (x, y, gesture) ->
            val start = 100L + index * 1000
            downTime = start
            event(Action.DOWN, start, point(7))
            event(Action.MOVE, start + 10, point(7, x, y))
            event(Action.UP, start + 30, point(7, x, y))
            assertEquals(Command(gesture), commands.last())
        }
        commands += recognizer.advanceTime(5000)
        assertEquals(4, commands.size)
    }

    @Test
    fun movementReturningToOriginNeverBecomesTap() {
        event(Action.DOWN, 100, point(7))
        event(Action.MOVE, 120, point(7, 15f))
        event(Action.UP, 130, point(7))
        commands += recognizer.advanceTime(1000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun targetReplacementBetweenTapsDoesNotPairIntoBack() {
        tap(100)
        downTime = 250
        event(Action.DOWN, 250, point(7), target = "new page")
        event(Action.UP, 280, point(7), target = "new page")
        assertTrue(commands.isEmpty())
        commands += recognizer.advanceTime(600)
        assertEquals(listOf(Command(Gesture.CONFIRM, "new page")), commands)
    }

    @Test
    fun slowSwipeIsStillDirectionalWhileLongStationaryPressIsNotATap() {
        event(Action.DOWN, 100, point(7))
        event(Action.MOVE, 400, point(7, 20f))
        event(Action.UP, 800, point(7, 40f))
        assertEquals(listOf(Command(Gesture.RIGHT)), commands)
    }

    @Test
    fun twoFingerTapWorksForBothReleaseOrdersAndReindexedRemainingPointer() {
        listOf(7, 42).forEach { firstReleased ->
            recognizer.cancel()
            commands.clear()
            twoFingerStart()
            event(Action.POINTER_UP, 150, point(42, 2f), point(7), changedId = firstReleased)
            val remaining = if (firstReleased == 7) 42 else 7
            event(Action.MOVE, 160, point(remaining, 2f))
            event(Action.UP, 170, point(remaining, 2f))
            event(Action.BUTTON_RELEASE, 170, point(remaining, 2f))
            commands += recognizer.advanceTime(1000)
            assertEquals(listOf(Command(Gesture.MENU)), commands)
        }
    }

    @Test
    fun beginningTwoFingerTapCancelsPendingSingleTap() {
        tap(100)
        downTime = 200
        event(Action.DOWN, 200, point(7))
        event(Action.POINTER_DOWN, 220, point(7), point(42), changedId = 42)
        assertNull(recognizer.nextTapAt)
        event(Action.POINTER_UP, 240, point(7), point(42), changedId = 7)
        event(Action.UP, 260, point(42))
        commands += recognizer.advanceTime(1000)
        assertEquals(listOf(Command(Gesture.MENU)), commands)
    }

    @Test
    fun movedRemainingFingerCancelsMenuEvenWhenItReturnsToOrigin() {
        twoFingerStart()
        event(Action.POINTER_UP, 140, point(7), point(42), changedId = 7)
        event(Action.MOVE, 150, point(42, 30f))
        event(Action.UP, 170, point(42))
        commands += recognizer.advanceTime(1000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun movedFirstFingerBeforeSecondTouchCancelsMenu() {
        event(Action.DOWN, 100, point(7))
        event(Action.MOVE, 110, point(7, 40f))
        event(Action.POINTER_DOWN, 120, point(7), point(42), changedId = 42)
        event(Action.POINTER_UP, 150, point(7), point(42), changedId = 42)
        event(Action.UP, 170, point(7))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun thirdPointerRejectsWholeSequence() {
        twoFingerStart()
        event(Action.POINTER_DOWN, 130, point(7), point(42), point(99), changedId = 99)
        event(Action.POINTER_UP, 140, point(7), point(42), point(99), changedId = 99)
        event(Action.POINTER_UP, 150, point(7), point(42), changedId = 7)
        event(Action.UP, 170, point(42))
        commands += recognizer.advanceTime(1000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun longSingleOrTwoFingerPressDoesNotActivate() {
        event(Action.DOWN, 100, point(7))
        event(Action.UP, 700, point(7))
        downTime = 1000
        event(Action.DOWN, 1000, point(7))
        event(Action.POINTER_DOWN, 1020, point(7), point(42), changedId = 42)
        event(Action.POINTER_UP, 1600, point(7), point(42), changedId = 7)
        event(Action.UP, 1620, point(42))
        commands += recognizer.advanceTime(2000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun cancelAndFlagCanceledReleaseRejectRemainder() {
        listOf(Action.CANCEL, Action.POINTER_UP).forEach { cancellation ->
            recognizer.cancel()
            twoFingerStart()
            event(cancellation, 150, point(7), point(42), changedId = 7, canceled = true)
            event(Action.UP, 170, point(42))
            event(Action.BUTTON_RELEASE, 170, point(42))
            commands += recognizer.advanceTime(1000)
            assertTrue(commands.isEmpty())
        }
    }

    @Test
    fun flagCanceledFinalUpDoesNotConfirmOrOpenMenu() {
        event(Action.DOWN, 100, point(7))
        event(Action.UP, 130, point(7), canceled = true)
        commands += recognizer.advanceTime(1000)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun lifecycleCancellationDropsPendingTapAndIncompleteSequence() {
        tap(100)
        recognizer.cancel()
        commands += recognizer.advanceTime(1000)
        event(Action.UP, 1100, point(7))
        assertTrue(commands.isEmpty())
        assertNull(recognizer.nextTapAt)
    }

    private fun tap(time: Long) {
        downTime = time
        event(Action.DOWN, time, point(7))
        event(Action.UP, time + 30, point(7))
    }

    private fun twoFingerStart() {
        downTime = 100
        event(Action.DOWN, 100, point(7))
        event(Action.POINTER_DOWN, 120, point(42), point(7), changedId = 42)
    }

    private fun point(
        id: Int,
        x: Float = 0f,
        y: Float = 0f,
    ) = RayNeoGestureRecognizer.Pointer(id, x, y)

    private fun event(
        action: Action,
        time: Long,
        vararg pointers: RayNeoGestureRecognizer.Pointer,
        changedId: Int = pointers.first().id,
        canceled: Boolean = false,
        target: Any? = "target",
    ) {
        commands +=
            recognizer.onEvent(
                RayNeoGestureRecognizer.Event(action, time, downTime, pointers.toList(), changedId, canceled),
                target,
            )
    }
}

private typealias Action = RayNeoGestureRecognizer.Action
private typealias Gesture = RayNeoGestureRecognizer.Gesture
private typealias Command = RayNeoGestureRecognizer.Command

package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNeoNavigationSessionTest {
    private val host = TestWindow()
    private val navigation = RayNeoNavigationSession(host)

    @Test
    fun deferredCenterNeverMovesToReplacementTargetOrInactiveWindow() {
        host.target = "new page"
        navigation.confirm("original")
        host.active = false
        navigation.confirm("new page")
        assertTrue(host.keys.isEmpty())
        assertEquals(0, host.recoveries)
    }

    @Test
    fun validConfirmationDispatchesCompletePairEvenIfDownIsHandled() {
        navigation.confirm("original")
        assertEquals(listOf(Key.CENTER to true, Key.CENTER to false), host.keys)
    }

    @Test
    fun handledComposeNavigationNeverRunsViewFallback() {
        navigation.move(Key.RIGHT)
        assertEquals(listOf(Key.RIGHT to true, Key.RIGHT to false), host.keys)
        assertEquals(0, host.fallbacks)
    }

    @Test
    fun unhandledKeyRunsOnlyOneViewFallbackWhenFocusHasNotMoved() {
        host.handled = false
        navigation.move(Key.DOWN)
        assertEquals(1, host.fallbacks)
    }

    @Test
    fun focusChangeDuringUnhandledDispatchPreventsSecondMove() {
        host.handled = false
        host.changeTargetOnKey = "next"
        navigation.move(Key.RIGHT)
        assertEquals("next", host.target)
        assertEquals(0, host.fallbacks)
    }

    @Test
    fun disappearingFocusIsRecoveredWithoutReplayingKey() {
        host.dropTargetOnKey = true
        navigation.move(Key.DOWN)
        assertEquals(1, host.recoveries)
        assertEquals(2, host.keys.size)
    }

    @Test
    fun oneTwoFingerGestureSendsExactlyOneMenuPairToItsWindow() {
        val recognizer = RayNeoGestureRecognizer(10f, 25f)
        val first = RayNeoGestureRecognizer.Pointer(7, 0f, 0f)
        val second = RayNeoGestureRecognizer.Pointer(42, 0f, 0f)

        fun send(
            action: RayNeoGestureRecognizer.Action,
            time: Long,
            pointers: List<RayNeoGestureRecognizer.Pointer>,
            id: Int,
        ) {
            recognizer.onEvent(RayNeoGestureRecognizer.Event(action, time, 100, pointers, id), "original").forEach {
                if (it.gesture == RayNeoGestureRecognizer.Gesture.MENU) navigation.menu()
            }
        }
        send(RayNeoGestureRecognizer.Action.DOWN, 100, listOf(first), 7)
        send(RayNeoGestureRecognizer.Action.POINTER_DOWN, 120, listOf(first, second), 42)
        send(RayNeoGestureRecognizer.Action.POINTER_UP, 140, listOf(first, second), 7)
        send(RayNeoGestureRecognizer.Action.UP, 160, listOf(second), 42)
        send(RayNeoGestureRecognizer.Action.BUTTON_RELEASE, 160, listOf(second), 42)
        assertEquals(listOf(Key.MENU to true, Key.MENU to false), host.keys)
    }

    private class TestWindow : RayNeoNavigationSession.Host<String> {
        var active = true
        var target: String? = "original"
        var handled = true
        var changeTargetOnKey: String? = null
        var dropTargetOnKey = false
        var fallbacks = 0
        var recoveries = 0
        val keys = mutableListOf<Pair<Key, Boolean>>()

        override fun isActive() = active

        override fun currentTarget() = target

        override fun recoverFocus() {
            recoveries++
            target = "default"
        }

        override fun dispatchKey(
            key: Key,
            down: Boolean,
        ): Boolean {
            keys += key to down
            if (dropTargetOnKey) target = null
            changeTargetOnKey?.let { target = it }
            return handled
        }

        override fun moveViewFocus(key: Key) {
            fallbacks++
        }
    }
}

private typealias Key = RayNeoNavigationSession.Key

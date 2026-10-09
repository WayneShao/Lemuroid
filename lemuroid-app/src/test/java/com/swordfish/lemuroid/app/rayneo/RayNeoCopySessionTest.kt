package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNeoCopySessionTest {
    private class Buffer(val width: Int, val height: Int)

    private val released = mutableListOf<Buffer>()
    private val session = RayNeoCopySession(::Buffer) { released.add(it) }

    @Test
    fun inactiveSessionDoesNotSchedule() {
        assertNull(session.schedule())
    }

    @Test
    fun repeatedResumeSchedulesOnlyOneFrameAndOneRequest() {
        session.setActive(true)
        val frame = requireNotNull(session.schedule())
        session.setActive(true)
        assertNull(session.schedule())

        val request = requireNotNull(session.begin(frame, 640, 480))
        assertNull(session.schedule())
        assertNull(session.begin(frame, 640, 480))
        assertTrue(session.complete(request))
        assertNotNull(session.schedule())
        assertNull(session.schedule())
    }

    @Test
    fun pauseRetainsInFlightBufferUntilLateCompletion() {
        val request = startRequest()

        session.setActive(false)
        assertTrue(released.isEmpty())
        assertNull(session.schedule())
        assertFalse(session.complete(request))
        assertEquals(listOf(request.buffer), released)
        assertNull(session.schedule())
        assertFalse(session.complete(request))
        assertEquals(1, released.size)
    }

    @Test
    fun resumeWaitsForObsoleteRequestWithoutDisplayingItsResult() {
        val oldRequest = startRequest()
        session.setActive(false)
        session.setActive(true)

        assertNull(session.schedule())
        assertFalse(session.complete(oldRequest))
        val frame = requireNotNull(session.schedule())
        val newRequest = requireNotNull(session.begin(frame, 640, 480))
        assertTrue(session.complete(newRequest))
        assertEquals(listOf(oldRequest.buffer), released)
    }

    @Test
    fun sourceOrSurfaceReplacementDiscardsInFlightResultWithoutEarlyRelease() {
        val request = startRequest()

        session.invalidate()
        assertTrue(released.isEmpty())
        assertNull(session.schedule())
        assertFalse(session.complete(request))
        assertEquals(listOf(request.buffer), released)
        assertNotNull(session.schedule())
    }

    @Test
    fun canceledFrameDoesNotConsumeFrameScheduledAfterResume() {
        session.setActive(true)
        val oldFrame = requireNotNull(session.schedule())
        session.setActive(false)
        session.setActive(true)
        val newFrame = requireNotNull(session.schedule())

        assertNull(session.begin(oldFrame, 640, 480))
        assertNotNull(session.begin(newFrame, 640, 480))
    }

    @Test
    fun completedBufferIsReusedUntilDimensionsChange() {
        val first = startRequest()
        assertTrue(session.complete(first))
        val second = requireNotNull(session.begin(requireNotNull(session.schedule()), 640, 480))
        assertSame(first.buffer, second.buffer)
        assertTrue(session.complete(second))

        val resized = requireNotNull(session.begin(requireNotNull(session.schedule()), 320, 240))
        assertEquals(listOf(first.buffer), released)
        assertEquals(320, resized.buffer.width)
        assertEquals(240, resized.buffer.height)
    }

    @Test
    fun requestSubmissionFailureCanCompleteAndRetryWithoutLeakingBuffer() {
        val rejected = startRequest()
        assertTrue(session.complete(rejected))

        val retry = requireNotNull(session.begin(requireNotNull(session.schedule()), 640, 480))
        assertSame(rejected.buffer, retry.buffer)
        session.setActive(false)
        assertFalse(session.complete(retry))
        assertEquals(listOf(rejected.buffer), released)
    }

    @Test
    fun zeroSizedFrameDoesNotStartRequestOrBlockRetry() {
        session.setActive(true)
        assertNull(session.begin(requireNotNull(session.schedule()), 0, 480))
        assertNotNull(session.begin(requireNotNull(session.schedule()), 640, 480))
    }

    @Test
    fun stoppingIdleSessionReleasesReusableBufferOnlyOnce() {
        val request = startRequest()
        assertTrue(session.complete(request))

        session.setActive(false)
        session.setActive(false)
        session.invalidate()
        assertEquals(listOf(request.buffer), released)
    }

    @Test
    fun duplicateCompletionDoesNotClearNewInFlightRequest() {
        val first = startRequest()
        assertTrue(session.complete(first))
        val second = requireNotNull(session.begin(requireNotNull(session.schedule()), 640, 480))

        assertFalse(session.complete(first))
        assertNull(session.schedule())
        assertTrue(session.complete(second))
        assertNotNull(session.schedule())
    }

    @Test
    fun allocationFailureLeavesSessionReadyToRetry() {
        var failAllocation = true
        val allocationSession =
            RayNeoCopySession(
                { width: Int, height: Int ->
                    check(!failAllocation)
                    Buffer(width, height)
                },
                { released.add(it) },
            )
        allocationSession.setActive(true)

        val failure =
            runCatching {
                allocationSession.begin(requireNotNull(allocationSession.schedule()), 640, 480)
            }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        failAllocation = false
        assertNotNull(allocationSession.begin(requireNotNull(allocationSession.schedule()), 640, 480))
    }

    private fun startRequest(): RayNeoCopySession.Request<Buffer> {
        session.setActive(true)
        return requireNotNull(session.begin(requireNotNull(session.schedule()), 640, 480))
    }
}

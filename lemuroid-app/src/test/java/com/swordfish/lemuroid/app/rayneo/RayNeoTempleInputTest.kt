package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNeoTempleInputTest {
    @Test
    fun acceptsOnlyVerifiedRightTempleNameIndependentOfDeviceId() {
        assertTrue(RayNeoTempleInput.isRightTemple("cyttsp5_mt"))
    }

    @Test
    fun leftTempleAndSystemSensorsNeverEnterRightGestureRecognizer() {
        listOf(null, "cyttsp6_mt", "CapSense Ch0", "mouse", "Xbox Wireless Controller", "cyttsp5_mt mouse").forEach {
            assertFalse(RayNeoTempleInput.isRightTemple(it))
        }
    }

    @Test
    fun appDeliveredLeftTempleAndCapSenseEventsAreRejectedBeforeChildren() {
        listOf("cyttsp6_mt", "CapSense Ch0", "CapSense Ch1").forEach {
            assertTrue(RayNeoTempleInput.rejectBeforeChildren(it))
        }
    }

    @Test
    fun ordinaryMouseAndGamepadRemainOnOriginalEventPath() {
        listOf(null, "mouse", "Xbox Wireless Controller", "cyttsp5_mt mouse", "cyttsp5_mt").forEach {
            assertFalse(RayNeoTempleInput.rejectBeforeChildren(it))
        }
    }
}

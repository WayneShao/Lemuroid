package com.swordfish.lemuroid.app.rayneo

/** X3 Pro identity verified by the device probe; numeric input IDs are not stable. */
internal object RayNeoTempleInput {
    fun isRightTemple(deviceName: String?): Boolean = deviceName == "cyttsp5_mt"

    fun rejectBeforeChildren(deviceName: String?): Boolean =
        deviceName == "cyttsp6_mt" || deviceName?.startsWith("CapSense ") == true
}

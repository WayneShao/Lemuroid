package com.swordfish.lemuroid.app.rayneo

import android.os.Build

object RayNeoDeviceUtils {
    /**
     * Detects whether we're running on a RayNeo X3 Pro device.
     *
     * Verified values from `adb shell getprop` on real hardware:
     *   ro.product.manufacturer = "RayNeo"
     *   ro.product.model        = "ARGF20"
     *   ro.product.brand        = "RayNeo"
     *   wm size                 = 1280x480
     *   wm density              = 160
     */
    fun isRayNeoDevice(): Boolean {
        return Build.MANUFACTURER.equals("RayNeo", ignoreCase = true) ||
            Build.MODEL.equals("ARGF20", ignoreCase = true)
    }
}

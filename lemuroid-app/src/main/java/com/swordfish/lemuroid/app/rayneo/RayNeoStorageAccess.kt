package com.swordfish.lemuroid.app.rayneo

import java.io.File

/** Validate a local ROM directory before changing the selection or scheduling an index. */
class RayNeoStorageAccess(
    private val directory: File,
    private val hasPermission: () -> Boolean,
    private val onDirectoryReady: (File) -> Unit,
) {
    enum class Result {
        PERMISSION_REQUIRED,
        DIRECTORY_UNAVAILABLE,
        READY,
    }

    private var completed = false

    fun connect(): Result {
        if (completed) return Result.READY
        if (!hasPermission()) return Result.PERMISSION_REQUIRED

        val accessible =
            try {
                (directory.isDirectory || directory.mkdirs()) && directory.listFiles() != null
            } catch (_: SecurityException) {
                false
            }
        if (!accessible) return Result.DIRECTORY_UNAVAILABLE

        onDirectoryReady(directory)
        completed = true
        return Result.READY
    }
}

package com.swordfish.lemuroid.app.rayneo

import java.io.File
import java.util.Locale

internal object RayNeoImageFiles {
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    /** Null means access failed; an empty list means the directory was read successfully. */
    fun list(directory: File): List<File>? {
        return try {
            if (!directory.isDirectory) return null
            directory.listFiles()
                ?.filter { it.isDirectory || (it.isFile && it.extension.lowercase(Locale.ROOT) in extensions) }
                ?.sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        .thenBy { it.name },
                )
        } catch (_: SecurityException) {
            null
        }
    }

    fun initialDirectory(
        previousPath: String?,
        storageRoot: File,
    ): File {
        val candidates =
            listOfNotNull(
                previousPath?.takeIf { it.isNotBlank() }?.let(::File),
                File(storageRoot, "Download"),
                storageRoot,
            )
        return candidates.firstOrNull { list(it) != null } ?: storageRoot
    }
}

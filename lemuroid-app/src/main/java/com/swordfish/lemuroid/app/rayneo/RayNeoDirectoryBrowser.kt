package com.swordfish.lemuroid.app.rayneo

import java.io.File
import java.io.IOException

/** Read-only browsing. Only the Activity's explicit confirmation changes the ROM selection. */
internal class RayNeoDirectoryBrowser(private val root: File) {
    data class Contents(val folders: List<File>, val fileCount: Int)

    fun initialDirectory(savedPath: String?): File =
        savedPath?.let(::File)?.let(::withinRoot)?.takeIf { it.isDirectory } ?: root

    fun parent(directory: File): File? = withinRoot(directory)?.takeUnless { it == root }?.parentFile?.let(::withinRoot)

    fun list(directory: File): Contents? {
        return try {
            val selected = withinRoot(directory) ?: return null
            val files = selected.listFiles() ?: return null
            Contents(
                files.filter { it.isDirectory && withinRoot(it) != null }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
                files.count { it.isFile },
            )
        } catch (_: SecurityException) {
            null
        }
    }

    private fun withinRoot(directory: File): File? {
        return try {
            val relative = directory.canonicalFile.relativeToOrNull(root.canonicalFile) ?: return null
            if (relative.path == ".." || relative.path.startsWith("..${File.separator}")) return null
            if (relative.path.isEmpty()) root else File(root, relative.path)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}

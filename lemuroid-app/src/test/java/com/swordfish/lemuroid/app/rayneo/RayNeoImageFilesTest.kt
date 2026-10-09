package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RayNeoImageFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun foldersComeFirstAndImageExtensionsAreCaseInsensitive() {
        val root = temporaryFolder.root
        File(root, "zFolder").mkdir()
        File(root, "aFolder").mkdir()
        listOf("z.PNG", "A.jpg", "b.WEBP", "c.gif", "d.heic", "e.avif", "rom.nes", "notes.txt")
            .forEach { File(root, it).writeText("fixture") }

        assertEquals(
            listOf("aFolder", "zFolder", "A.jpg", "b.WEBP", "c.gif", "d.heic", "e.avif", "z.PNG"),
            RayNeoImageFiles.list(root)?.map { it.name },
        )
        assertEquals("fixture", File(root, "rom.nes").readText())
    }

    @Test
    fun emptyDirectoryAndUnlistableDirectoryHaveDifferentResults() {
        val unlistable =
            object : File(temporaryFolder.root, "unlistable") {
                override fun isDirectory() = true

                override fun listFiles(): Array<File>? = null
            }

        assertEquals(emptyList<File>(), RayNeoImageFiles.list(temporaryFolder.root))
        assertNull(RayNeoImageFiles.list(unlistable))
    }

    @Test
    fun securityFailureIsReportedAsUnavailable() {
        val restricted =
            object : File(temporaryFolder.root, "restricted") {
                override fun isDirectory(): Boolean = throw SecurityException("denied")
            }

        assertNull(RayNeoImageFiles.list(restricted))
    }

    @Test
    fun ordinaryFileIsNotAnEmptyDirectory() {
        assertNull(RayNeoImageFiles.list(temporaryFolder.newFile("cover.png")))
    }

    @Test
    fun previousReadableFolderIsKeptEvenWhenEmpty() {
        val previous = temporaryFolder.newFolder("Covers")
        temporaryFolder.newFolder("Download")

        assertEquals(previous, RayNeoImageFiles.initialDirectory(previous.path, temporaryFolder.root))
    }

    @Test
    fun missingPreviousFolderFallsBackToDownloadWithoutCreatingIt() {
        val missing = File(temporaryFolder.root, "missing")
        val download = temporaryFolder.newFolder("Download")

        assertEquals(download, RayNeoImageFiles.initialDirectory(missing.path, temporaryFolder.root))
        assertFalse(missing.exists())
    }

    @Test
    fun downloadAbsentFallsBackToStorageRootWithoutCreatingFolders() {
        val root = temporaryFolder.root

        assertEquals(root, RayNeoImageFiles.initialDirectory(null, root))
        assertFalse(File(root, "Download").exists())
        assertNotNull(RayNeoImageFiles.list(root))
    }
}

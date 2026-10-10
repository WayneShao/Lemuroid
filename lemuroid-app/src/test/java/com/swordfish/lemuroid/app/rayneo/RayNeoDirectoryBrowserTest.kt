package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RayNeoDirectoryBrowserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun listsFoldersSeparatelyFromFilesAndDoesNotChangeThem() {
        val root = temporaryFolder.newFolder("storage")
        val games = File(root, "GameROMs").apply { mkdir() }
        File(root, "backup").mkdir()
        val rom = File(root, "game.nes").apply { writeText("ROM") }
        val contents = RayNeoDirectoryBrowser(root).list(root)!!

        assertEquals(listOf("backup", "GameROMs"), contents.folders.map { it.name })
        assertEquals(1, contents.fileCount)
        assertEquals("ROM", rom.readText())
        assertEquals(emptyList<File>(), games.listFiles()!!.toList())
    }

    @Test
    fun keepsExistingSelectionAndFallsBackToRootForMissingPath() {
        val root = temporaryFolder.newFolder("storage")
        val games = File(root, "GameROMs").apply { mkdir() }
        val browser = RayNeoDirectoryBrowser(root)

        assertEquals(games, browser.initialDirectory(games.path))
        assertEquals(root, browser.initialDirectory(File(root, "missing").path))
        assertFalse(File(root, "missing").exists())
    }

    @Test
    fun parentNavigationStopsAtStorageRoot() {
        val root = temporaryFolder.newFolder("storage")
        val child = File(root, "GameROMs").apply { mkdir() }
        val browser = RayNeoDirectoryBrowser(root)

        assertEquals(root, browser.parent(child))
        assertNull(browser.parent(root))
    }

    @Test
    fun rejectsOutsideRootAndFileTargets() {
        val root = temporaryFolder.newFolder("storage")
        val outside = temporaryFolder.newFolder("private")
        val file = File(root, "game.nes").apply { writeText("ROM") }
        val browser = RayNeoDirectoryBrowser(root)

        assertEquals(root, browser.initialDirectory(outside.path))
        assertNull(browser.list(outside))
        assertNull(browser.list(file))
    }

    @Test
    fun emptyDirectoryIsReadableAndSelectable() {
        val root = temporaryFolder.newFolder("storage")
        val contents = RayNeoDirectoryBrowser(root).list(root)!!

        assertEquals(emptyList<File>(), contents.folders)
        assertEquals(0, contents.fileCount)
    }
}

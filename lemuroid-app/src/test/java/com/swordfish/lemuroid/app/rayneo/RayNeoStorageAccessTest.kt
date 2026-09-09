package com.swordfish.lemuroid.app.rayneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RayNeoStorageAccessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun deniedPermissionDoesNotCreateDirectoryOrSelectIt() {
        val directory = File(temporaryFolder.root, "Games")
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(directory, { false }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.PERMISSION_REQUIRED, access.connect())
        assertFalse(directory.exists())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun permissionGrantedAfterRetryCreatesDirectoryAndSelectsItOnlyOnce() {
        val directory = File(temporaryFolder.root, "Games")
        val selected = mutableListOf<File>()
        var permissionGranted = false
        val access = RayNeoStorageAccess(directory, { permissionGranted }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.PERMISSION_REQUIRED, access.connect())
        permissionGranted = true
        assertEquals(RayNeoStorageAccess.Result.READY, access.connect())
        assertEquals(RayNeoStorageAccess.Result.READY, access.connect())
        assertTrue(directory.isDirectory)
        assertEquals(listOf(directory), selected)
    }

    @Test
    fun existingDirectoryAndGameFilesArePreserved() {
        val directory = temporaryFolder.newFolder("ExistingGames")
        val game = File(directory, "game.nes").apply { writeText("existing ROM") }
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(directory, { true }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.READY, access.connect())
        assertEquals("existing ROM", game.readText())
        assertEquals(listOf(directory), selected)
    }

    @Test
    fun existingEmptyDirectoryIsAValidSelection() {
        val directory = temporaryFolder.newFolder("EmptyGames")
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(directory, { true }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.READY, access.connect())
        assertEquals(listOf(directory), selected)
    }

    @Test
    fun fileAtDirectoryPathDoesNotChangeSelection() {
        val file = temporaryFolder.newFile("Games")
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(file, { true }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.DIRECTORY_UNAVAILABLE, access.connect())
        assertTrue(file.isFile)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun unlistableDirectoryDoesNotChangeSelection() {
        val directory =
            object : File(temporaryFolder.root, "Games") {
                override fun isDirectory() = true

                override fun listFiles(): Array<File>? = null
            }
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(directory, { true }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.DIRECTORY_UNAVAILABLE, access.connect())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun directoryCreationFailureDoesNotChangeSelection() {
        val parentFile = temporaryFolder.newFile("parent")
        val directory = File(parentFile, "Games")
        val selected = mutableListOf<File>()
        val access = RayNeoStorageAccess(directory, { true }, { selected.add(it) })

        assertEquals(RayNeoStorageAccess.Result.DIRECTORY_UNAVAILABLE, access.connect())
        assertFalse(directory.exists())
        assertTrue(selected.isEmpty())
    }
}

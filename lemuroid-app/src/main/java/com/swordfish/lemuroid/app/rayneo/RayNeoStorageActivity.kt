package com.swordfish.lemuroid.app.rayneo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** A regular content page so the existing Activity wrapper also renders permission feedback in both eyes. */
class RayNeoStorageActivity : ComponentActivity() {
    private var message by mutableStateOf(R.string.rayneo_storage_permission_required)
    private var needsPermission by mutableStateOf(true)
    private var directory by mutableStateOf(File("/sdcard"))
    private var contents by mutableStateOf<RayNeoDirectoryBrowser.Contents?>(null)
    private var loading by mutableStateOf(false)
    private val browser = RayNeoDirectoryBrowser(File("/sdcard"))
    private var listing: Job? = null
    private var generation = 0
    private var resumed = false
    private var completed = false

    private val preferences by lazy { SharedPreferencesHelper.getLegacySharedPreferences(this) }
    private val legacyKey by lazy { getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder) }
    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directory =
            browser.initialDirectory(
                savedInstanceState?.getString(STATE_DIRECTORY) ?: preferences.getString(legacyKey, null),
            )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val parent = browser.parent(directory)
                    if (!needsPermission && parent != null) openDirectory(parent) else finish()
                }
            },
        )
        // Install content once, before DualEyeActivityWrapper's posted wrapping callback.
        setContent {
            RayNeoStorageScreen(
                directory = directory.absolutePath,
                message = message,
                needsPermission = needsPermission,
                loading = loading,
                folders = contents?.folders.orEmpty(),
                fileCount = contents?.fileCount,
                canGoUp = browser.parent(directory) != null,
                onGrantPermission = ::requestStoragePermission,
                onRetry = ::refreshAccess,
                onOpenFolder = ::openDirectory,
                onUp = { browser.parent(directory)?.let(::openDirectory) },
                onRoot = { openDirectory(File("/sdcard")) },
                onSelect = ::selectDirectory,
                onCancel = ::finish,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Recheck the actual appop after Settings, an ADB grant, or Activity recreation.
        refreshAccess()
    }

    override fun onPause() {
        resumed = false
        generation++
        listing?.cancel()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DIRECTORY, directory.absolutePath)
        super.onSaveInstanceState(outState)
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun refreshAccess() {
        openDirectory(directory)
    }

    private fun openDirectory(target: File) {
        if (!resumed || completed || isFinishing) return
        val request = ++generation
        listing?.cancel()
        directory = target
        contents = null
        needsPermission = !hasStoragePermission()
        loading = false
        if (needsPermission) {
            message = R.string.rayneo_storage_permission_required
            return
        }
        loading = true
        message = R.string.rayneo_storage_loading
        listing =
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { browser.list(target) }
                if (request != generation || !resumed || completed || isFinishing) return@launch
                loading = false
                needsPermission = !hasStoragePermission()
                contents = result.takeUnless { needsPermission }
                message =
                    when {
                        needsPermission -> R.string.rayneo_storage_permission_required
                        result == null -> R.string.rayneo_storage_directory_unavailable
                        else -> R.string.rayneo_storage_choose
                    }
            }
    }

    private fun selectDirectory() {
        if (loading || contents == null || completed || !resumed) return
        val access =
            RayNeoStorageAccess(directory, ::hasStoragePermission) { selected ->
                preferences.edit()
                    .putString(legacyKey, selected.absolutePath)
                    .remove(getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder))
                    .apply()
                LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
            }
        when (access.connect()) {
            RayNeoStorageAccess.Result.READY -> {
                completed = true
                finish()
            }
            RayNeoStorageAccess.Result.PERMISSION_REQUIRED -> {
                needsPermission = true
                message = R.string.rayneo_storage_permission_required
            }
            RayNeoStorageAccess.Result.DIRECTORY_UNAVAILABLE -> {
                contents = null
                needsPermission = false
                message = R.string.rayneo_storage_directory_unavailable
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        val settingsIntents =
            listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        for (settingsIntent in settingsIntents) {
            try {
                startActivity(settingsIntent)
                return
            } catch (error: ActivityNotFoundException) {
                Timber.w(error, "RayNeo storage settings activity is missing")
            } catch (error: SecurityException) {
                Timber.w(error, "RayNeo storage settings activity is restricted")
            }
        }
        message = R.string.rayneo_storage_settings_unavailable
    }

    private companion object {
        const val STATE_DIRECTORY = "directory"
    }
}

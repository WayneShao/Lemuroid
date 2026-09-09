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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber
import java.io.File

/** A regular content page so the existing Activity wrapper also renders permission feedback in both eyes. */
class RayNeoStorageActivity : ComponentActivity() {
    private var message by mutableStateOf(R.string.rayneo_storage_permission_required)
    private var needsPermission by mutableStateOf(true)

    private val preferences by lazy { SharedPreferencesHelper.getLegacySharedPreferences(this) }
    private val legacyKey by lazy { getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder) }
    private val directory by lazy {
        File(preferences.getString(legacyKey, null)?.takeIf { it.isNotBlank() } ?: "/sdcard/Games")
    }
    private val storageAccess by lazy {
        RayNeoStorageAccess(directory, ::hasStoragePermission) { selected ->
            preferences.edit()
                .putString(legacyKey, selected.absolutePath)
                .remove(getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder))
                .apply()
            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        }
    }
    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Install content once, before DualEyeActivityWrapper's posted wrapping callback.
        setContent {
            RayNeoStorageScreen(
                directory = directory.absolutePath,
                message = message,
                needsPermission = needsPermission,
                onGrantPermission = ::requestStoragePermission,
                onRetry = ::refreshAccess,
                onCancel = ::finish,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheck the actual appop after Settings, an ADB grant, or Activity recreation.
        refreshAccess()
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun refreshAccess() {
        if (isFinishing) return
        when (storageAccess.connect()) {
            RayNeoStorageAccess.Result.READY -> finish()
            RayNeoStorageAccess.Result.PERMISSION_REQUIRED -> {
                needsPermission = true
                message = R.string.rayneo_storage_permission_required
            }
            RayNeoStorageAccess.Result.DIRECTORY_UNAVAILABLE -> {
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
}

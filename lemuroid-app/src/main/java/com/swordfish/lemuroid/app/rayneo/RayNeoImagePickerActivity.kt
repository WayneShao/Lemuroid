package com.swordfish.lemuroid.app.rayneo

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Same-app fallback for missing image pickers. Returns RESULT_OK with a validated file URI in data.
 * The manifest disables HWUI only for this static page; the Activity wrapper supplies stereo output.
 */
class RayNeoImagePickerActivity : ComponentActivity() {
    private val storageRoot = File("/sdcard")
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private var directory: File? = null
    private var entries = emptyList<File>()
    private var operation: Job? = null
    private var generation = 0L
    private var resumed = false
    private var completed = false

    private lateinit var pathView: TextView
    private lateinit var statusView: TextView
    private lateinit var listView: ListView
    private lateinit var upButton: Button
    private lateinit var grantButton: Button
    private lateinit var retryButton: Button
    private lateinit var cancelButton: Button
    private val adapter = ImageFileAdapter()
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        directory = savedInstanceState?.getString(STATE_DIRECTORY)?.let(::File)
        installContent()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val parent = parentDirectory()
                    if (hasStoragePermission() && parent != null) {
                        openDirectory(parent)
                    } else {
                        cancelPicker()
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Settings does not return a permission grant result. Always check the actual app access.
        refreshAccess()
    }

    override fun onPause() {
        resumed = false
        cancelOperation()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directory?.let { outState.putString(STATE_DIRECTORY, it.absolutePath) }
        super.onSaveInstanceState(outState)
    }

    private fun installContent() {
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        content.addView(textView(20f).apply { setText(R.string.rayneo_image_picker_title) })
        pathView = textView(14f).apply { maxLines = 2 }
        content.addView(pathView)
        statusView = textView(16f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(statusView)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        upButton = actionButton(R.string.rayneo_image_picker_up) { parentDirectory()?.let(::openDirectory) }
        grantButton = actionButton(R.string.rayneo_image_picker_grant, ::requestStoragePermission)
        retryButton = actionButton(R.string.rayneo_image_picker_retry, ::refreshAccess)
        cancelButton = actionButton(R.string.rayneo_image_picker_cancel, ::cancelPicker)
        listOf(upButton, grantButton, retryButton, cancelButton).forEach { button ->
            actions.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        }
        content.addView(actions)

        listView =
            ListView(this).apply {
                adapter = this@RayNeoImagePickerActivity.adapter
                isFocusable = true
                isFocusableInTouchMode = true
                setSelector(ColorDrawable(FOCUS_COLOR))
                divider = ColorDrawable(Color.DKGRAY)
                dividerHeight = dp(1)
                setOnItemClickListener { _, _, position, _ ->
                    entries.getOrNull(position)?.let { file ->
                        if (file.isDirectory) openDirectory(file) else selectImage(file)
                    }
                }
            }
        content.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Install one native content tree before the global Activity wrapper's posted callback.
        setContentView(content)
    }

    private fun textView(size: Float): TextView =
        TextView(this).apply {
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun actionButton(
        label: Int,
        action: () -> Unit,
    ): Button =
        Button(this).apply {
            setText(label)
            textSize = 16f
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), 0)
            background =
                StateListDrawable().apply {
                    addState(intArrayOf(-android.R.attr.state_enabled), ColorDrawable(Color.DKGRAY))
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(FOCUS_COLOR))
                    addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(FOCUS_COLOR))
                    addState(intArrayOf(), ColorDrawable(BUTTON_COLOR))
                }
            setOnClickListener { action() }
        }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun refreshAccess() {
        if (!resumed || completed || isFinishing) return
        if (!hasStoragePermission()) {
            showPermissionRequired()
        } else {
            openDirectory(directory)
        }
    }

    private fun showPermissionRequired() {
        cancelOperation()
        entries = emptyList()
        adapter.notifyDataSetChanged()
        listView.isEnabled = false
        grantButton.visibility = View.VISIBLE
        upButton.isEnabled = false
        pathView.text = directory?.absolutePath.orEmpty()
        statusView.setText(R.string.rayneo_image_picker_permission_required)
        grantButton.requestFocus()
    }

    private fun requestStoragePermission() {
        if (completed) return
        if (hasStoragePermission()) {
            refreshAccess()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                Timber.w(error, "Image picker storage settings are missing")
            } catch (error: SecurityException) {
                Timber.w(error, "Image picker storage settings are restricted")
            }
        }
        statusView.setText(R.string.rayneo_image_picker_settings_unavailable)
        retryButton.requestFocus()
    }

    private fun openDirectory(target: File?) {
        if (!resumed || completed || isFinishing) return
        if (!hasStoragePermission()) {
            showPermissionRequired()
            return
        }
        cancelOperation()
        val requestGeneration = generation
        directory = target
        entries = emptyList()
        adapter.notifyDataSetChanged()
        listView.isEnabled = false
        grantButton.visibility = View.GONE
        upButton.isEnabled = parentDirectory() != null
        pathView.text = target?.absolutePath.orEmpty()
        statusView.setText(R.string.rayneo_image_picker_loading)
        operation =
            lifecycleScope.launch {
                val (selectedDirectory, files) =
                    withContext(Dispatchers.IO) {
                        val selected =
                            target ?: RayNeoImageFiles.initialDirectory(
                                preferences.getString(LAST_DIRECTORY, null),
                                storageRoot,
                            )
                        selected to RayNeoImageFiles.list(selected)
                    }
                if (!canApply(requestGeneration)) return@launch
                if (!hasStoragePermission()) {
                    showPermissionRequired()
                    return@launch
                }
                directory = selectedDirectory
                pathView.text = selectedDirectory.absolutePath
                upButton.isEnabled = parentDirectory() != null
                entries = files.orEmpty()
                adapter.notifyDataSetChanged()
                listView.isEnabled = files != null && files.isNotEmpty()
                statusView.setText(
                    when {
                        files == null -> R.string.rayneo_image_picker_directory_unavailable
                        files.isEmpty() -> R.string.rayneo_image_picker_empty
                        else -> R.string.rayneo_image_picker_choose
                    },
                )
                if (listView.isEnabled) {
                    listView.requestFocus()
                    listView.setSelection(0)
                } else {
                    retryButton.requestFocus()
                }
            }
    }

    private fun selectImage(file: File) {
        if (!resumed || completed || !listView.isEnabled) return
        if (!hasStoragePermission()) {
            showPermissionRequired()
            return
        }
        cancelOperation()
        val requestGeneration = generation
        listView.isEnabled = false
        statusView.setText(R.string.rayneo_image_picker_checking)
        operation =
            lifecycleScope.launch {
                val mimeType = withContext(Dispatchers.IO) { readableImageType(file) }
                if (!canApply(requestGeneration)) return@launch
                if (!hasStoragePermission()) {
                    showPermissionRequired()
                    return@launch
                }
                if (mimeType == null) {
                    statusView.setText(R.string.rayneo_image_picker_invalid_image)
                    listView.isEnabled = true
                    listView.requestFocus()
                    return@launch
                }
                completed = true
                file.parentFile?.let { preferences.edit().putString(LAST_DIRECTORY, it.absolutePath).apply() }
                // This result never leaves our package; do not expose a file URI to another app.
                val result = Intent().setPackage(packageName).setDataAndType(Uri.fromFile(file), mimeType)
                setResult(Activity.RESULT_OK, result)
                finish()
            }
    }

    private fun readableImageType(file: File): String? {
        return try {
            if (!file.isFile) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
            options.outMimeType?.takeIf { options.outWidth > 0 && options.outHeight > 0 && it.startsWith("image/") }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun parentDirectory(): File? = directory?.takeUnless { it == storageRoot }?.parentFile

    private fun canApply(requestGeneration: Long): Boolean =
        requestGeneration == generation && resumed && !completed && !isFinishing && !isDestroyed

    private fun cancelOperation() {
        generation++
        operation?.cancel()
        operation = null
    }

    private fun cancelPicker() {
        completed = true
        cancelOperation()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class ImageFileAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): File = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val row =
                convertView as? TextView ?: textView(18f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(48)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            val file = getItem(position)
            row.text =
                if (file.isDirectory) getString(R.string.rayneo_image_picker_folder, file.name) else file.name
            return row
        }
    }

    companion object {
        private const val PREFERENCES = "rayneo_image_picker"
        private const val LAST_DIRECTORY = "last_directory"
        private const val STATE_DIRECTORY = "directory"
        private const val FOCUS_COLOR = 0xff315b88.toInt()
        private const val BUTTON_COLOR = 0xff252525.toInt()
    }
}

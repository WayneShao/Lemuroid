package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Preserve upstream GetContent result handling; substitute only the missing RayNeo chooser. */
class RayNeoImagePickerContract : ActivityResultContracts.GetContent() {
    override fun createIntent(
        context: Context,
        input: String,
    ): Intent {
        val intent = super.createIntent(context, input)
        return if (RayNeoDeviceUtils.isRayNeoDevice() && intent.resolveActivity(context.packageManager) == null) {
            Intent(context, RayNeoImagePickerActivity::class.java)
        } else {
            intent
        }
    }
}

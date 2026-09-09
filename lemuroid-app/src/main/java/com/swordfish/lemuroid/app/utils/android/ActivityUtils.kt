package com.swordfish.lemuroid.app.utils.android

import android.app.Activity
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.ComposeView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidAlertDialog
import com.swordfish.lemuroid.app.rayneo.RayNeoDeviceUtils

// TODO COMPOSE... How do they look in the post compose world?
fun Activity.displayErrorDialog(
    messageId: Int,
    actionLabelId: Int,
    action: () -> Unit,
) {
    displayErrorDialog(resources.getString(messageId), resources.getString(actionLabelId), action)
}

fun Activity.displayErrorDialog(
    message: String,
    actionLabel: String,
    action: () -> Unit,
) {
    if (RayNeoDeviceUtils.isRayNeoDevice()) {
        // This zero-sized host owns the separate dual-eye Dialog composition.
        val host = ComposeView(this)
        addContentView(host, ViewGroup.LayoutParams(0, 0))
        host.setContent {
            AppTheme {
                LemuroidAlertDialog(
                    onDismissRequest = {},
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = {
                            host.disposeComposition()
                            (host.parent as? ViewGroup)?.removeView(host)
                            action()
                        }) {
                            Text(actionLabel)
                        }
                    },
                )
            }
        }
        return
    }
    AlertDialog.Builder(this)
        .setMessage(message)
        .setPositiveButton(actionLabel) { _, _ -> action() }
        .setCancelable(false)
        .show()
}

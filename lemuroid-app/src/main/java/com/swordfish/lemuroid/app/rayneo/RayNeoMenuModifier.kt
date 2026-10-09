package com.swordfish.lemuroid.app.rayneo

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/** The library's card/list entry is the common context-menu action for the focused game. */
fun Modifier.rayNeoContextMenu(onMenu: () -> Unit): Modifier =
    if (RayNeoDeviceUtils.isRayNeoDevice()) {
        onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode == KeyEvent.KEYCODE_MENU) {
                if (key.action == KeyEvent.ACTION_UP && !key.isCanceled) onMenu()
                true
            } else {
                false
            }
        }
    } else {
        this
    }

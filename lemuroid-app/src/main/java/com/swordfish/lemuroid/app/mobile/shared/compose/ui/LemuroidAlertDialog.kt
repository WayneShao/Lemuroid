package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.rayneo.DualEyeDialog
import com.swordfish.lemuroid.app.rayneo.RayNeoDeviceUtils
import androidx.compose.material3.AlertDialog as MaterialAlertDialog

/** Shared modal entry point for settings, help, input binding, and confirmation dialogs. */
@Composable
fun LemuroidAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    if (!RayNeoDeviceUtils.isRayNeoDevice()) {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
        )
        return
    }

    DualEyeDialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .pointerInput(onDismissRequest) {
                        detectTapGestures(onTap = { onDismissRequest() })
                    }.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, content = title)
                    }
                    if (text != null) {
                        Box(Modifier.weight(1f, fill = false)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.app.rayneo.RayNeoDeviceUtils

/** Keep upstream sheet content and actions together; only the window presentation varies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LemuroidModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (RayNeoDeviceUtils.isRayNeoDevice()) {
        LemuroidAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {},
            text = { Column(content = content) },
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, content = content)
    }
}

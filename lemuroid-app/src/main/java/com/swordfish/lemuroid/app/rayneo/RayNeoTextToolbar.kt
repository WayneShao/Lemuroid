package com.swordfish.lemuroid.app.rayneo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Text actions stay inside the current eye's content instead of Android's independent floating toolbar. */
private class RayNeoTextToolbar : TextToolbar {
    var actions by mutableStateOf<List<Pair<Int, () -> Unit>>>(emptyList())
    override val status: TextToolbarStatus
        get() = if (actions.isEmpty()) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        actions =
            listOfNotNull(
                onCopyRequested?.let { android.R.string.copy to it },
                onPasteRequested?.let { android.R.string.paste to it },
                onCutRequested?.let { android.R.string.cut to it },
                onSelectAllRequested?.let { android.R.string.selectAll to it },
            )
    }

    override fun hide() {
        actions = emptyList()
    }
}

@Composable
fun RayNeoTextToolbarHost(content: @Composable () -> Unit) {
    val toolbar = remember { RayNeoTextToolbar() }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box {
            content()
            if (toolbar.status == TextToolbarStatus.Shown) {
                Surface(modifier = Modifier.align(Alignment.TopCenter), tonalElevation = 8.dp) {
                    Row {
                        toolbar.actions.forEach { (label, action) ->
                            TextButton(onClick = {
                                toolbar.hide()
                                action()
                            }) { Text(stringResource(label)) }
                        }
                    }
                }
            }
        }
    }
}

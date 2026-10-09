package com.swordfish.lemuroid.app.rayneo

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A separate dialog window with one composition, drawn to both eyes without bitmap copies.
 * The host Activity's wrapper cannot see this window. Keep its own content and dismiss action here.
 */
@Composable
fun DualEyeDialog(
    onDismissRequest: () -> Unit,
    eyeWidth: Int = DualEyeLayout.SINGLE_EYE_WIDTH,
    content: @Composable () -> Unit,
) {
    val compositionContext = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val currentDismiss = rememberUpdatedState(onDismissRequest)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                RayNeoInputFrameLayout(context).apply {
                    val dualEye = DualEyeLayout(context, eyeWidth)
                    dualEye.addView(
                        ComposeView(context).apply {
                            setParentCompositionContext(compositionContext)
                            setContent { RayNeoTextToolbarHost { currentContent.value() } }
                        },
                        FrameLayout.LayoutParams(eyeWidth, FrameLayout.LayoutParams.MATCH_PARENT),
                    )
                    addView(
                        dualEye,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    val input = RightEyeGestureOverlay(context) { currentDismiss.value() }
                    templeInput = input
                    addView(
                        input,
                        FrameLayout.LayoutParams(eyeWidth, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                            leftMargin = eyeWidth
                        },
                    )
                }
            },
        )
    }
}

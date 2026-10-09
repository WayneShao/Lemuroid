package com.swordfish.lemuroid.app.rayneo

import android.graphics.RectF
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.libretrodroid.GLRetroView

/**
 * Game screen for RayNeo X3 Pro.
 *
 * The current window is split into equal eye regions (typically 640×480 each).
 * The left eye owns GLRetroView; the right eye copies that same rendered Surface.
 *
 * No on-screen touch controls are shown — the user is expected to use a
 * Bluetooth gamepad (Xbox, PS, etc.), which Lemuroid already supports natively.
 *
 * Temple input is handled by the Activity's shared RayNeo input adapter.
 */
@Composable
fun RayNeoGameScreen(viewModel: BaseGameScreenViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Mutable ref so the update lambda of the mirror AndroidView can connect to the source.
    val retroViewRef = remember { arrayOfNulls<GLRetroView>(1) }
    val mirrorViewRef = remember { arrayOfNulls<RayNeoMirrorView>(1) }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Left eye: GLRetroView ────────────────────────────────────────────────
        AndroidView(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            factory = { context ->
                viewModel.createRetroView(context, lifecycleOwner).also { view ->
                    retroViewRef[0] = view
                    // Force the viewport to fill the entire left-half container.
                    // (MobileGameScreen sets this dynamically based on touch-pad layout;
                    //  on RayNeo there are no touch controls, so full extent is correct.)
                    view.viewport = RectF(0f, 0f, 1f, 1f)
                    // Keep the viewportAlignment supplied by the upstream view-model factory.
                    // If the mirror view was already created, connect it now.
                    mirrorViewRef[0]?.attachSource(view)
                }
            },
            onReset = null,
            onRelease = { view ->
                if (retroViewRef[0] === view) {
                    retroViewRef[0] = null
                    mirrorViewRef[0]?.detachSource()
                }
            },
        )

        // ── Right eye: PixelCopy mirror ──────────────────────────────────────────
        AndroidView(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            factory = { context ->
                RayNeoMirrorView(context).also { view ->
                    mirrorViewRef[0] = view
                    // If the GLRetroView was already created, connect immediately.
                    retroViewRef[0]?.let { view.attachSource(it) }
                }
            },
            onReset = null,
            update = { view ->
                view.bindLifecycle(lifecycleOwner.lifecycle)
                retroViewRef[0]?.let { view.attachSource(it) }
            },
            onRelease = { view ->
                if (mirrorViewRef[0] === view) mirrorViewRef[0] = null
                view.release()
            },
        )
    }
}

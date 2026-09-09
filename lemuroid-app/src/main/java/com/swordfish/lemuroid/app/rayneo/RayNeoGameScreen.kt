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
 * Layout: 1280×480 full screen (the physical display of the glasses)
 *   Left  640px  →  GLRetroView  (the actual game, rendered by LibretroDroid)
 *   Right 640px  →  RayNeoMirrorView  (PixelCopy mirror of the left eye)
 *
 * No on-screen touch controls are shown — the user is expected to use a
 * Bluetooth gamepad (Xbox, PS, etc.), which Lemuroid already supports natively.
 *
 * The right-eye back gesture is installed by DualEyeActivityWrapper.
 */
@Composable
fun RayNeoGameScreen(viewModel: BaseGameScreenViewModel) {
    val lifecycle = LocalLifecycleOwner.current

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
                viewModel.createRetroView(context, lifecycle).also { view ->
                    retroViewRef[0] = view
                    // Force the viewport to fill the entire left-half container.
                    // (MobileGameScreen sets this dynamically based on touch-pad layout;
                    //  on RayNeo there are no touch controls, so full extent is correct.)
                    view.viewport = RectF(0f, 0f, 1f, 1f)
                    // If the mirror view was already created, connect it now.
                    mirrorViewRef[0]?.attachSource(view)
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
        )
    }
}

package com.swordfish.lemuroid.app.rayneo

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel

/**
 * Game activity for RayNeo X3 Pro.
 *
 * Differences from the standard GameActivity:
 *  - Uses RayNeoGameScreen (dual-eye layout, no on-screen touch pad)
 *  - Declared in the manifest as singleTask + landscape
 *
 * DualEyeActivityWrapper adds the right-eye gesture overlay separately from the game surfaces.
 */
class RayNeoGameActivity : BaseGameActivity() {
    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        RayNeoGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = keyCode == KeyEvent.KEYCODE_MENU || super.onKeyDown(keyCode, event)

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyUp(keyCode, event)
        if (!event.isCanceled) requestGameMenu()
        return true
    }
}

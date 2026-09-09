package com.swordfish.lemuroid.app.rayneo

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
 * Mirror-lens input will be added later once the display is verified.
 */
class RayNeoGameActivity : BaseGameActivity() {

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        RayNeoGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}

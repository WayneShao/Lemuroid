package com.swordfish.lemuroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.rayneo.RayNeoDeviceUtils
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView

@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    if (isGameReady) {
        gameScreen(viewModel)
    } else {
        val message = if (gameState is GameViewModelRetroGameView.GameState.Loading) gameState.message else null
        if (RayNeoDeviceUtils.isRayNeoDevice()) {
            // The game Activity is intentionally excluded from root duplication, including while loading.
            Row(Modifier.fillMaxSize()) {
                repeat(2) { LoadingContent(message, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            LoadingContent(message, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun LoadingContent(
    message: String?,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            AnimatedVisibility(message != null) {
                Text(text = message.orEmpty(), color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

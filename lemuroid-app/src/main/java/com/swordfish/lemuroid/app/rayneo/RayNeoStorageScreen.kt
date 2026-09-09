package com.swordfish.lemuroid.app.rayneo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme

@Composable
fun RayNeoStorageScreen(
    directory: String,
    @StringRes message: Int,
    needsPermission: Boolean,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.rayneo_storage_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                Text(directory, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (needsPermission) {
                        Button(onClick = onGrantPermission) {
                            Text(stringResource(R.string.rayneo_storage_grant))
                        }
                    }
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.rayneo_storage_retry))
                    }
                    TextButton(onClick = onCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}

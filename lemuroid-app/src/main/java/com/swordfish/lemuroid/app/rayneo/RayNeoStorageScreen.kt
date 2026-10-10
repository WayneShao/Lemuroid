package com.swordfish.lemuroid.app.rayneo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import java.io.File

@Composable
fun RayNeoStorageScreen(
    directory: String,
    @StringRes message: Int,
    needsPermission: Boolean,
    loading: Boolean,
    folders: List<File>,
    fileCount: Int?,
    canGoUp: Boolean,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
    onOpenFolder: (File) -> Unit,
    onUp: () -> Unit,
    onRoot: () -> Unit,
    onSelect: () -> Unit,
    onCancel: () -> Unit,
) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.rayneo_storage_title), style = MaterialTheme.typography.titleLarge)
                Text(directory, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsPermission) {
                        Button(onClick = onGrantPermission) {
                            Text(stringResource(R.string.rayneo_storage_grant))
                        }
                    } else {
                        OutlinedButton(onClick = onUp, enabled = canGoUp && !loading) {
                            Text(stringResource(R.string.rayneo_storage_up))
                        }
                        OutlinedButton(onClick = onRoot, enabled = !loading) {
                            Text(stringResource(R.string.rayneo_storage_root))
                        }
                    }
                    TextButton(onClick = onRetry, enabled = !loading) {
                        Text(stringResource(R.string.rayneo_storage_retry))
                    }
                }
                key(directory) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(folders, key = { it.absolutePath }) { folder ->
                            OutlinedButton(
                                onClick = { onOpenFolder(folder) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    folder.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("›")
                            }
                        }
                        if (folders.isEmpty() && fileCount != null) {
                            item { Text(stringResource(R.string.rayneo_storage_no_subfolders)) }
                        }
                    }
                }
                if (fileCount != null) {
                    Text(
                        stringResource(R.string.rayneo_storage_contents, folders.size, fileCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSelect, enabled = fileCount != null && !loading && !needsPermission) {
                        Text(stringResource(R.string.rayneo_storage_select))
                    }
                    TextButton(onClick = onCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}

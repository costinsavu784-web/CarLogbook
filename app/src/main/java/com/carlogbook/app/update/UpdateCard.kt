package com.carlogbook.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Banner shown at the top of the garage when an update is available or a check is in progress.
 * Renders nothing in the Idle state.
 */
@Composable
fun UpdateCard(state: UpdateState, vm: UpdateViewModel, modifier: Modifier = Modifier) {
    if (state is UpdateState.Idle) return
    val context = LocalContext.current

    // Re-read the "install unknown apps" permission whenever we come back from Settings.
    var canInstall by remember { mutableStateOf(UpdateInstaller.canInstall(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = UpdateInstaller.canInstall(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                UpdateState.Idle -> Unit

                UpdateState.Checking -> {
                    Text("Checking for updates…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                UpdateState.UpToDate -> {
                    Text("You have the latest version.", style = MaterialTheme.typography.titleMedium)
                    Actions { TextButton(onClick = vm::dismiss) { Text("OK") } }
                }

                is UpdateState.Available -> {
                    Text("Version ${state.update.versionName} is available", style = MaterialTheme.typography.titleMedium)
                    if (state.update.notes.isNotBlank()) {
                        Text(state.update.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Actions {
                        TextButton(onClick = vm::dismiss) { Text("Later") }
                        TextButton(onClick = { vm.download(state.update) }) {
                            Text("Download" + sizeSuffix(state.update.apkSizeBytes))
                        }
                    }
                }

                is UpdateState.Downloading -> {
                    Text("Downloading version ${state.update.versionName}…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Actions { TextButton(onClick = vm::cancelDownload) { Text("Cancel") } }
                }

                is UpdateState.ReadyToInstall -> {
                    Text("Version ${state.update.versionName} downloaded", style = MaterialTheme.typography.titleMedium)
                    if (canInstall) {
                        Text("Android will ask you to confirm the installation.", style = MaterialTheme.typography.bodyMedium)
                        Actions {
                            TextButton(onClick = vm::dismiss) { Text("Later") }
                            TextButton(onClick = { vm.install(state.apk) }) { Text("Install") }
                        }
                    } else {
                        Text(
                            "To install updates, allow Car Logbook to install apps. You only need to do this once.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Actions {
                            TextButton(onClick = vm::dismiss) { Text("Later") }
                            TextButton(onClick = { UpdateInstaller.openInstallPermissionSettings(context) }) {
                                Text("Open settings")
                            }
                        }
                    }
                }

                is UpdateState.Failed -> {
                    Text("Update check failed", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    Actions {
                        TextButton(onClick = vm::dismiss) { Text("Dismiss") }
                        TextButton(onClick = vm::checkNow) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { content() }
}

private fun sizeSuffix(bytes: Long): String =
    if (bytes <= 0) "" else " (%.1f MB)".format(bytes / 1_048_576.0)

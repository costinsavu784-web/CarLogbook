package com.carlogbook.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.R
import com.carlogbook.app.data.BackupCodec
import com.carlogbook.app.data.GarageBackup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val cars by vm.cars.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<GarageBackup?>(null) }

    // The system "Save as" dialog. The user picks the folder (Downloads, SD card, Drive…).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val s = vm.exportBackup(uri)
                snackbar.showSnackbar(context.getString(R.string.saved_summary, s.cars, s.services))
            } catch (e: Exception) {
                snackbar.showSnackbar(context.getString(R.string.export_failed, e.message ?: ""))
            } finally {
                busy = false
            }
        }
    }

    // The system "Open" dialog. We read and validate the file first, then ask before replacing anything.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                pendingRestore = vm.readBackup(uri)
            } catch (e: Exception) {
                snackbar.showSnackbar(if (e is BackupCodec.InvalidBackup) context.getString(e.messageRes) else e.message ?: context.getString(R.string.could_not_read_file))
            } finally {
                busy = false
            }
        }
    }

    pendingRestore?.let { garage ->
        val services = garage.serviceCount
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            message = stringResource(R.string.restore_confirm_message, garage.cars.size, services, cars.size),
            confirmLabel = stringResource(R.string.replace),
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    busy = true
                    try {
                        val s = vm.importBackup(garage)
                        snackbar.showSnackbar(context.getString(R.string.restored_summary, s.cars, s.services))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(context.getString(R.string.restore_failed, e.message ?: ""))
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_and_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.export), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.export_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { exportLauncher.launch(BackupCodec.suggestedFileName()) },
                        enabled = !busy && cars.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_garage))
                    }
                    if (cars.isEmpty()) {
                        Text(stringResource(R.string.nothing_to_export), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restore), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.restore_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restore_from_file))
                    }
                }
            }

            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            Text(
                stringResource(R.string.backup_footer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

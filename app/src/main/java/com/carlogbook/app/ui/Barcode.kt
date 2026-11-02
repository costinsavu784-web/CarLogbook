package com.carlogbook.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlogbook.app.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Opens Google's full-screen scanner and returns the raw text of the first code it reads. */
object BarcodeScan {
    fun start(context: Context, onResult: (String) -> Unit, onError: () -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { code -> code.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let(onResult) }
            .addOnFailureListener { onError() }
        // Cancelling the scanner is not an error: nothing happens.
    }
}

/** Small scan icon, meant as the trailing icon of a part-number text field. */
@Composable
fun ScanIconButton(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.scan_failed)
    IconButton(onClick = {
        BarcodeScan.start(context, onScanned) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
    }
}

/** "Scan part" text button: scans a code and hands it back so the caller can add a new part line. */
@Composable
fun ScanPartButton(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.scan_failed)
    TextButton(onClick = {
        BarcodeScan.start(context, onScanned) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.scan_part))
    }
}

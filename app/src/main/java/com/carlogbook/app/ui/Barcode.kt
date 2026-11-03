package com.carlogbook.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlogbook.app.R
import com.carlogbook.app.domain.ProductLookup
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** What a scan produced, after the user has reviewed it. */
data class ScannedPart(
    val code: String,
    val atMillis: Long,
    val name: String,
    val supplier: String
)

/** A raw read: the code text and the symbology it came from. */
data class ScanRead(val code: String, val format: Int, val atMillis: Long) {
    val formatName: String
        get() = when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_QR_CODE -> "QR"
            Barcode.FORMAT_DATA_MATRIX -> "DataMatrix"
            else -> ""
        }
}

/** Opens Google's full-screen scanner and returns the first code it reads. */
object BarcodeScan {
    fun start(context: Context, onResult: (ScanRead) -> Unit, onError: () -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { b ->
                b.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { onResult(ScanRead(it, b.format, System.currentTimeMillis())) }
            }
            .addOnFailureListener { onError() }
        // Cancelling the scanner is not an error: nothing happens.
    }
}

/** Small scan icon, meant as the trailing icon of a part-number text field. Returns the raw code. */
@Composable
fun ScanIconButton(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.scan_failed)
    IconButton(onClick = {
        BarcodeScan.start(context, { onScanned(it.code) }) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
    }
}

/**
 * "Scan part": scans a code, then shows a review dialog with the code read, the product found
 * online (editable), and a "Scan again" button for when the camera caught the wrong barcode.
 */
@Composable
fun ScanPartButton(vm: MainViewModel, onScanned: (ScannedPart) -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.scan_failed)
    var pending by remember { mutableStateOf<ScanRead?>(null) }

    fun scan() {
        BarcodeScan.start(context, { pending = it }) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }

    pending?.let { read ->
        ScanReviewDialog(
            vm = vm,
            read = read,
            onScanAgain = { pending = null; scan() },
            onCancel = { pending = null },
            onAdd = { name, supplier ->
                vm.rememberProduct(read.code, name, supplier)
                onScanned(ScannedPart(read.code, read.atMillis, name, supplier))
                pending = null
            }
        )
    }

    TextButton(onClick = { scan() }) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.scan_part))
    }
}

@Composable
private fun ScanReviewDialog(
    vm: MainViewModel,
    read: ScanRead,
    onScanAgain: () -> Unit,
    onCancel: () -> Unit,
    onAdd: (name: String, supplier: String) -> Unit
) {
    val isGtin = ProductLookup.looksLikeGtin(read.code)
    val storeLabel = ProductLookup.looksLikeStoreLabel(read.code)
    var looking by remember(read.code) { mutableStateOf(true) }
    var found by remember(read.code) { mutableStateOf<Boolean?>(null) }
    var remembered by remember(read.code) { mutableStateOf(false) }
    // A part-number label is not a retail code: it is the supplier number itself.
    var name by remember(read.code) { mutableStateOf("") }
    var supplier by remember(read.code) { mutableStateOf(if (isGtin) "" else read.code) }

    LaunchedEffect(read.code) {
        // 1. Something you identified before? Instant and offline.
        val known = vm.knownProduct(read.code)
        if (known != null) {
            name = known.name
            supplier = known.supplierNumber.ifBlank { supplier }
            remembered = true
            found = true
            looking = false
            return@LaunchedEffect
        }
        // 2. Otherwise ask the public databases, unless it is clearly a shop sticker.
        if (isGtin && !storeLabel) {
            val product = ProductLookup.lookup(read.code)
            found = product != null
            if (product != null) {
                name = product.displayName
                supplier = product.model.ifBlank { product.brand }
            }
        }
        looking = false
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.scan_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.scan_code_read, read.code, read.formatName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    storeLabel && !remembered -> Text(
                        stringResource(R.string.scan_store_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    looking -> Row {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lookup_searching), style = MaterialTheme.typography.bodySmall)
                    }
                    remembered -> Text(
                        stringResource(R.string.lookup_remembered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    isGtin && found == false -> Text(
                        stringResource(R.string.lookup_not_found) + " " + stringResource(R.string.lookup_will_remember),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !isGtin -> Text(
                        stringResource(R.string.scan_not_retail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.part_or_material)) },
                    singleLine = true,
                    enabled = !looking,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(R.string.supplier_number)) },
                    singleLine = true,
                    enabled = !looking,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), supplier.trim()) }, enabled = !looking && name.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onScanAgain) { Text(stringResource(R.string.scan_again)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

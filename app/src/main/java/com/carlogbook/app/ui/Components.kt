package com.carlogbook.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** A text box that only accepts digits. */
@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 7,
    hint: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= maxLength && v.all { c -> c.isDigit() }) onChange(v)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (hint != null) {
            { Text(hint) }
        } else {
            null
        },
        modifier = modifier
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Common jobs offered as quick picks when adding a service. */
val DefaultJobNames = listOf(
    "Oil change",
    "Oil filter",
    "Air filter",
    "Cabin filter",
    "Fuel filter",
    "Spark plugs",
    "Brake pads (front)",
    "Brake pads (rear)",
    "Brake discs",
    "Brake fluid",
    "Coolant",
    "Transmission fluid",
    "Tyres",
    "Tyre rotation",
    "Wheel alignment",
    "Battery",
    "Wiper blades",
    "Timing belt",
    "Inspection",
    "Technical inspection"
)

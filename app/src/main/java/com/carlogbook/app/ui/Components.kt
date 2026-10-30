package com.carlogbook.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.carlogbook.app.R
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Fmt

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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Jobs offered as quick picks when adding a service: every built-in component, in the current language. */
val DefaultJobNames: List<String> get() = Catalog.allComponentNames

/** A logged job name as the user should see it: translated if it is a built-in component. */
fun jobLabel(jobName: String): String = Catalog.builtInComponentFor(jobName)?.displayName ?: jobName

/** "Repeat every 10,000 km or every 12 months", or empty when the service has no interval. */
@Composable
fun repeatText(service: Service): String {
    val every = stringResource(R.string.every_x)
    val parts = listOfNotNull(
        service.intervalKm?.let { every.format(Fmt.km(it)) },
        service.intervalMonths?.let { every.format(Fmt.months(it)) }
    )
    if (parts.isEmpty()) return ""
    return stringResource(R.string.repeat_every, parts.joinToString(" ${stringResource(R.string.or)} "))
}

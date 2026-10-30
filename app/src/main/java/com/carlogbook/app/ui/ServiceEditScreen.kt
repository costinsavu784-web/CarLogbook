package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.Fmt
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEditScreen(
    vm: MainViewModel,
    carId: Long,
    serviceId: Long,
    onDone: () -> Unit
) {
    val isNew = serviceId == 0L

    var loaded by rememberSaveable { mutableStateOf(false) }
    var jobName by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var intervalKm by rememberSaveable { mutableStateOf("") }
    var intervalMonths by rememberSaveable { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(serviceId) {
        if (!loaded) {
            if (isNew) {
                val car = vm.getCar(carId)
                if (car != null && car.currentOdometerKm > 0) {
                    odometer = car.currentOdometerKm.toString()
                }
            } else {
                val service = vm.getService(serviceId)
                if (service != null) {
                    jobName = service.jobName
                    dateDay = service.dateEpochDay
                    odometer = service.odometerKm.toString()
                    notes = service.notes
                    intervalKm = service.intervalKm?.toString() ?: ""
                    intervalMonths = service.intervalMonths?.toString() ?: ""
                }
            }
            loaded = true
        }
    }

    val usedNames by vm.jobNames.collectAsStateWithLifecycle()
    val suggestions = (usedNames + DefaultJobNames)
        .distinctBy { it.lowercase() }
        .filter { jobName.isBlank() || (it.contains(jobName, ignoreCase = true) && !it.equals(jobName, ignoreCase = true)) }
        .take(15)

    val odometerValue = odometer.toIntOrNull()
    val kmInterval = intervalKm.toIntOrNull()?.takeIf { it > 0 }
    val monthsInterval = intervalMonths.toIntOrNull()?.takeIf { it > 0 }
    val canSave = jobName.isNotBlank() && odometerValue != null

    val nextDue = buildList {
        if (kmInterval != null && odometerValue != null) {
            add("at ${Fmt.km(odometerValue + kmInterval)}")
        }
        if (monthsInterval != null) {
            val day = LocalDate.ofEpochDay(dateDay).plusMonths(monthsInterval.toLong()).toEpochDay()
            add("on ${Fmt.date(day)}")
        }
    }.joinToString(" or ")

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateDay = Math.floorDiv(it, MILLIS_PER_DAY) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this service?",
            message = "This entry will be removed from the history.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteService(serviceId)
                showDelete = false
                onDone()
            },
            onDismiss = { showDelete = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add service" else "Edit service") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = jobName,
                onValueChange = { jobName = it },
                label = { Text("What did you do?") },
                placeholder = { Text("For example: Brake pads") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (suggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { name ->
                        SuggestionChip(onClick = { jobName = name }, label = { Text(name) })
                    }
                }
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Date: ${Fmt.date(dateDay)}")
            }

            NumberField(
                value = odometer,
                onChange = { odometer = it },
                label = "Odometer (km)",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Remind me for the next one (optional)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                NumberField(
                    value = intervalKm,
                    onChange = { intervalKm = it },
                    label = "Every … km",
                    maxLength = 6,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = intervalMonths,
                    onChange = { intervalMonths = it },
                    label = "Every … months",
                    maxLength = 3,
                    modifier = Modifier.weight(1f)
                )
            }
            if (nextDue.isNotEmpty()) {
                Text(
                    "Next due $nextDue, whichever comes first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    if (odometerValue != null) {
                        vm.saveService(
                            Service(
                                id = serviceId,
                                carId = carId,
                                jobName = jobName.trim(),
                                dateEpochDay = dateDay,
                                odometerKm = odometerValue,
                                notes = notes.trim(),
                                intervalKm = kmInterval,
                                intervalMonths = monthsInterval
                            )
                        )
                        onDone()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) { Text("Save service") }

            if (!isNew) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete this service", color = MaterialTheme.colorScheme.error) }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

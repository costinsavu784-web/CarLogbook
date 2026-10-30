package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** One job in the form, with its own optional reminder interval. */
private data class JobEntry(
    val name: String,
    val intervalKm: String = "",
    val intervalMonths: String = ""
)

/** Keeps the job list across rotation and process death. */
private val JobsSaver = listSaver<SnapshotStateList<JobEntry>, String>(
    save = { list -> list.flatMap { listOf(it.name, it.intervalKm, it.intervalMonths) } },
    restore = { flat ->
        mutableStateListOf<JobEntry>().apply {
            flat.chunked(3).forEach { add(JobEntry(it[0], it[1], it[2])) }
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceEditScreen(
    vm: MainViewModel,
    carId: Long,
    serviceId: Long,
    onDone: () -> Unit
) {
    val isNew = serviceId == 0L
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(false) }
    val jobs = rememberSaveable(saver = JobsSaver) { mutableStateListOf<JobEntry>() }
    var jobInput by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

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
                    jobs.add(
                        JobEntry(
                            name = service.jobName,
                            intervalKm = service.intervalKm?.toString() ?: "",
                            intervalMonths = service.intervalMonths?.toString() ?: ""
                        )
                    )
                    dateDay = service.dateEpochDay
                    odometer = service.odometerKm.toString()
                    notes = service.notes
                }
            }
            loaded = true
        }
    }

    /** Adds a job to the list, prefilling the interval you used last time for it. */
    fun addJob(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty() || jobs.any { it.name.equals(name, ignoreCase = true) }) {
            jobInput = ""
            return
        }
        jobInput = ""
        scope.launch {
            val last = vm.lastInterval(carId, name)
            if (jobs.none { it.name.equals(name, ignoreCase = true) }) {
                jobs.add(
                    JobEntry(
                        name = name,
                        intervalKm = last?.intervalKm?.toString() ?: "",
                        intervalMonths = last?.intervalMonths?.toString() ?: ""
                    )
                )
            }
        }
    }

    val usedNames by vm.jobNames.collectAsStateWithLifecycle()
    val suggestions = (usedNames + DefaultJobNames)
        .distinctBy { it.lowercase() }
        .filter { name -> jobs.none { it.name.equals(name, ignoreCase = true) } }
        .filter { jobInput.isBlank() || it.contains(jobInput, ignoreCase = true) }
        .take(15)

    val odometerValue = odometer.toIntOrNull()
    val canSave = jobs.isNotEmpty() && jobs.all { it.name.isNotBlank() } && odometerValue != null

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
            if (isNew) {
                Text("What did you do?", style = MaterialTheme.typography.titleSmall)

                if (jobs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        jobs.forEach { job ->
                            InputChip(
                                selected = true,
                                onClick = { jobs.remove(job) },
                                label = { Text(job.name) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove ${job.name}")
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = jobInput,
                    onValueChange = { jobInput = it },
                    label = { Text(if (jobs.isEmpty()) "Job" else "Add another job") },
                    placeholder = { Text("For example: Brake pads") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addJob(jobInput) }),
                    trailingIcon = {
                        if (jobInput.isNotBlank()) {
                            IconButton(onClick = { addJob(jobInput) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add job")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { name ->
                            SuggestionChip(onClick = { addJob(name) }, label = { Text(name) })
                        }
                    }
                }
            } else {
                val job = jobs.firstOrNull() ?: JobEntry("")
                OutlinedTextField(
                    value = job.name,
                    onValueChange = { new -> if (jobs.isNotEmpty()) jobs[0] = job.copy(name = new) },
                    label = { Text("What did you do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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

            if (jobs.isNotEmpty()) {
                Text(
                    if (jobs.size == 1) "Remind me for the next one (optional)" else "Remind me for the next ones (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            jobs.forEachIndexed { index, job ->
                if (index > 0) HorizontalDivider()
                IntervalEditor(
                    job = job,
                    showName = jobs.size > 1,
                    dateDay = dateDay,
                    odometerValue = odometerValue,
                    onChange = { jobs[index] = it }
                )
            }

            Button(
                onClick = {
                    if (odometerValue != null) {
                        val services = jobs.map { job ->
                            Service(
                                id = if (isNew) 0L else serviceId,
                                carId = carId,
                                jobName = job.name.trim(),
                                dateEpochDay = dateDay,
                                odometerKm = odometerValue,
                                notes = notes.trim(),
                                intervalKm = job.intervalKm.toIntOrNull()?.takeIf { it > 0 },
                                intervalMonths = job.intervalMonths.toIntOrNull()?.takeIf { it > 0 }
                            )
                        }
                        vm.saveServices(services)
                        onDone()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(if (jobs.size > 1) "Save ${jobs.size} services" else "Save service")
            }

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

@Composable
private fun IntervalEditor(
    job: JobEntry,
    showName: Boolean,
    dateDay: Long,
    odometerValue: Int?,
    onChange: (JobEntry) -> Unit
) {
    val kmInterval = job.intervalKm.toIntOrNull()?.takeIf { it > 0 }
    val monthsInterval = job.intervalMonths.toIntOrNull()?.takeIf { it > 0 }

    val nextDue = buildList {
        if (kmInterval != null && odometerValue != null) add("at ${Fmt.km(odometerValue + kmInterval)}")
        if (monthsInterval != null) {
            val day = LocalDate.ofEpochDay(dateDay).plusMonths(monthsInterval.toLong()).toEpochDay()
            add("on ${Fmt.date(day)}")
        }
    }.joinToString(" or ")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showName) {
            Text(job.name, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            NumberField(
                value = job.intervalKm,
                onChange = { onChange(job.copy(intervalKm = it)) },
                label = "Every … km",
                maxLength = 6,
                modifier = Modifier.weight(1f)
            )
            NumberField(
                value = job.intervalMonths,
                onChange = { onChange(job.copy(intervalMonths = it)) },
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
    }
}

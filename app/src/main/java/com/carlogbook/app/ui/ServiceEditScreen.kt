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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.R
import com.carlogbook.app.data.Service
import com.carlogbook.app.data.ServiceDraft
import com.carlogbook.app.data.ServicePart
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** One part or material used for a job, as typed in the form. */
private data class PartEntry(
    val name: String = "",
    val quantity: String = "",
    val oem: String = "",
    val supplier: String = ""
) {
    val isBlank: Boolean get() = name.isBlank() && quantity.isBlank() && oem.isBlank() && supplier.isBlank()
}

/** One job in the form, with its own optional reminder interval and parts list. */
private data class JobEntry(
    val name: String,
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val parts: List<PartEntry> = emptyList()
)

/** Keeps the job list across rotation and process death. Flat string list; parts are 4 strings each. */
private val JobsSaver = listSaver<SnapshotStateList<JobEntry>, String>(
    save = { list ->
        buildList {
            for (j in list) {
                add(j.name); add(j.intervalKm); add(j.intervalMonths); add(j.parts.size.toString())
                for (p in j.parts) { add(p.name); add(p.quantity); add(p.oem); add(p.supplier) }
            }
        }
    },
    restore = { flat ->
        mutableStateListOf<JobEntry>().apply {
            var i = 0
            while (i + 3 < flat.size) {
                val name = flat[i]; val km = flat[i + 1]; val months = flat[i + 2]
                val n = flat[i + 3].toIntOrNull() ?: 0
                i += 4
                val parts = List(n) { k ->
                    val b = i + k * 4
                    PartEntry(flat[b], flat[b + 1], flat[b + 2], flat[b + 3])
                }
                i += n * 4
                add(JobEntry(name, km, months, parts))
            }
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceEditScreen(
    vm: MainViewModel,
    carId: Long,
    serviceId: Long,
    onDone: () -> Unit,
    /** Job to start with when opened from a component in the overview. */
    initialJob: String? = null
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

    /** Same component already in the list, in any spelling or language? */
    fun hasJob(name: String) = jobs.any { Catalog.componentKey(it.name) == Catalog.componentKey(name) }

    /** Builds a job entry prefilled with the interval and parts used last time for this job. */
    suspend fun prefilledJob(name: String): JobEntry {
        val last = vm.lastInterval(carId, name)
        val parts = vm.lastParts(carId, name).map { PartEntry(it.name, it.quantity, it.oemNumber, it.supplierNumber) }
        return JobEntry(
            name = name,
            intervalKm = last?.intervalKm?.toString() ?: "",
            intervalMonths = last?.intervalMonths?.toString() ?: "",
            parts = parts
        )
    }

    LaunchedEffect(serviceId) {
        if (!loaded) {
            if (isNew) {
                val car = vm.getCar(carId)
                if (car != null && car.currentOdometerKm > 0) {
                    odometer = car.currentOdometerKm.toString()
                }
                val job = initialJob?.trim()
                if (!job.isNullOrEmpty()) jobs.add(prefilledJob(job))
            } else {
                val service = vm.getService(serviceId)
                if (service != null) {
                    val parts = vm.getParts(serviceId).map { PartEntry(it.name, it.quantity, it.oemNumber, it.supplierNumber) }
                    jobs.add(
                        JobEntry(
                            name = service.jobName,
                            intervalKm = service.intervalKm?.toString() ?: "",
                            intervalMonths = service.intervalMonths?.toString() ?: "",
                            parts = parts
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

    fun addJob(rawName: String) {
        val name = rawName.trim()
        jobInput = ""
        if (name.isEmpty() || hasJob(name)) return
        scope.launch {
            val entry = prefilledJob(name)
            if (!hasJob(name)) jobs.add(entry)
        }
    }

    val usedNames by vm.jobNames.collectAsStateWithLifecycle()
    val placed by vm.placements.collectAsStateWithLifecycle()
    val suggestions = (DefaultJobNames + placed.map { it.name } + usedNames.map { jobLabel(it) })
        .distinctBy { Catalog.componentKey(it) }
        .filter { name -> !hasJob(name) }
        .filter { jobInput.isBlank() || it.contains(jobInput, ignoreCase = true) }
        .take(15)

    val odometerValue = odometer.toIntOrNull()
    val canSave = jobs.isNotEmpty() && jobs.all { it.name.isNotBlank() } && odometerValue != null
    val defaultPartName = stringResource(R.string.part_default_name)

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateDay = Math.floorDiv(it, MILLIS_PER_DAY) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_service_title),
            message = stringResource(R.string.delete_service_message),
            confirmLabel = stringResource(R.string.delete),
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
                title = { Text(stringResource(if (isNew) R.string.add_service else R.string.edit_service)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // ---- Step 1: what was done -------------------------------------------------------
            if (isNew) {
                Text(stringResource(R.string.what_did_you_do), style = MaterialTheme.typography.titleSmall)

                if (jobs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        jobs.forEach { job ->
                            val label = jobLabel(job.name)
                            InputChip(
                                selected = true,
                                onClick = { jobs.remove(job) },
                                label = { Text(label) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_job, label)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = jobInput,
                    onValueChange = { jobInput = it },
                    label = { Text(stringResource(if (jobs.isEmpty()) R.string.job else R.string.add_another_job)) },
                    placeholder = { Text(stringResource(R.string.job_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addJob(jobInput) }),
                    trailingIcon = {
                        if (jobInput.isNotBlank()) {
                            IconButton(onClick = { addJob(jobInput) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_job))
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
                    label = { Text(stringResource(R.string.what_did_you_do)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Step 2: when and at what mileage -------------------------------------------
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.date_is, Fmt.date(dateDay)))
            }

            NumberField(
                value = odometer,
                onChange = { odometer = it },
                label = stringResource(R.string.odometer_km),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes_optional)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Step 3: details per job ----------------------------------------------------
            if (jobs.isNotEmpty()) {
                Text(
                    stringResource(R.string.details_per_job),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.details_per_job_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            jobs.forEachIndexed { index, job ->
                JobDetailsCard(
                    job = job,
                    dateDay = dateDay,
                    odometerValue = odometerValue,
                    onChange = { jobs[index] = it }
                )
            }

            // ---- Save ----------------------------------------------------------------------
            Button(
                onClick = {
                    if (odometerValue != null) {
                        val drafts = jobs.map { job ->
                            ServiceDraft(
                                service = Service(
                                    id = if (isNew) 0L else serviceId,
                                    carId = carId,
                                    jobName = job.name.trim(),
                                    dateEpochDay = dateDay,
                                    odometerKm = odometerValue,
                                    notes = notes.trim(),
                                    intervalKm = job.intervalKm.toIntOrNull()?.takeIf { it > 0 },
                                    intervalMonths = job.intervalMonths.toIntOrNull()?.takeIf { it > 0 }
                                ),
                                parts = job.parts
                                    .filter { !it.isBlank }
                                    .map {
                                        ServicePart(
                                            serviceId = 0,
                                            name = it.name.trim().ifEmpty { defaultPartName },
                                            quantity = it.quantity.trim(),
                                            oemNumber = it.oem.trim(),
                                            supplierNumber = it.supplier.trim()
                                        )
                                    }
                            )
                        }
                        vm.saveServices(drafts)
                        onDone()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    if (jobs.size > 1) stringResource(R.string.save_n_services, jobs.size)
                    else stringResource(R.string.save_service)
                )
            }

            if (!isNew) {
                TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete_this_service), color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

/** Reminder interval and parts list for one job. */
@Composable
private fun JobDetailsCard(
    job: JobEntry,
    dateDay: Long,
    odometerValue: Int?,
    onChange: (JobEntry) -> Unit
) {
    val kmInterval = job.intervalKm.toIntOrNull()?.takeIf { it > 0 }
    val monthsInterval = job.intervalMonths.toIntOrNull()?.takeIf { it > 0 }

    val nextDue = buildList {
        if (kmInterval != null && odometerValue != null) add(stringResource(R.string.next_due_at_km, Fmt.km(odometerValue + kmInterval)))
        if (monthsInterval != null) {
            val day = LocalDate.ofEpochDay(dateDay).plusMonths(monthsInterval.toLong()).toEpochDay()
            add(stringResource(R.string.next_due_on_date, Fmt.date(day)))
        }
    }.joinToString(" ${stringResource(R.string.or)} ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(jobLabel(job.name), style = MaterialTheme.typography.titleMedium)

            Text(stringResource(R.string.remind_next), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                NumberField(
                    value = job.intervalKm,
                    onChange = { onChange(job.copy(intervalKm = it)) },
                    label = stringResource(R.string.every_km),
                    maxLength = 6,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = job.intervalMonths,
                    onChange = { onChange(job.copy(intervalMonths = it)) },
                    label = stringResource(R.string.every_months),
                    maxLength = 3,
                    modifier = Modifier.weight(1f)
                )
            }
            if (nextDue.isNotEmpty()) {
                Text(
                    stringResource(R.string.next_due, nextDue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                stringResource(R.string.parts_used),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
            job.parts.forEachIndexed { pIndex, part ->
                PartEditor(
                    part = part,
                    onChange = { updated ->
                        onChange(job.copy(parts = job.parts.toMutableList().also { it[pIndex] = updated }))
                    },
                    onRemove = {
                        onChange(job.copy(parts = job.parts.toMutableList().also { it.removeAt(pIndex) }))
                    }
                )
            }
            TextButton(onClick = { onChange(job.copy(parts = job.parts + PartEntry())) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (job.parts.isEmpty()) R.string.add_part else R.string.add_another_part))
            }
        }
    }
}

@Composable
private fun PartEditor(part: PartEntry, onChange: (PartEntry) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = part.name,
                    onValueChange = { onChange(part.copy(name = it)) },
                    label = { Text(stringResource(R.string.part_or_material)) },
                    placeholder = { Text(stringResource(R.string.part_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_part))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                OutlinedTextField(
                    value = part.quantity,
                    onValueChange = { onChange(part.copy(quantity = it)) },
                    label = { Text(stringResource(R.string.quantity)) },
                    placeholder = { Text(stringResource(R.string.quantity_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
                OutlinedTextField(
                    value = part.oem,
                    onValueChange = { onChange(part.copy(oem = it)) },
                    label = { Text(stringResource(R.string.oem_number)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = part.supplier,
                onValueChange = { onChange(part.copy(supplier = it)) },
                label = { Text(stringResource(R.string.supplier_number)) },
                placeholder = { Text(stringResource(R.string.supplier_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            )
        }
    }
}

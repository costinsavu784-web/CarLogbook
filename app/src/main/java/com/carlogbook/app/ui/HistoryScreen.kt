package com.carlogbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.data.LastCar
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: MainViewModel,
    carId: Long,
    onBack: () -> Unit,
    onAddService: () -> Unit,
    onEditService: (Long) -> Unit,
    onUpcoming: () -> Unit,
    onEditCar: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(carId) { LastCar.set(context, carId) }

    val carFlow = remember(carId) { vm.car(carId) }
    val servicesFlow = remember(carId) { vm.services(carId) }
    val car by carFlow.collectAsStateWithLifecycle(initialValue = null)
    val services by servicesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentCar = car

    var showOdometer by remember { mutableStateOf(false) }
    if (showOdometer && currentCar != null) {
        OdometerDialog(
            current = currentCar.currentOdometerKm,
            onDismiss = { showOdometer = false },
            onSave = { km ->
                vm.updateOdometer(carId, km)
                showOdometer = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentCar?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onUpcoming) {
                        Icon(Icons.Default.Notifications, contentDescription = "Reminders")
                    }
                    IconButton(onClick = onEditCar) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit car")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddService,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add service") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentCar != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current odometer", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Fmt.km(currentCar.currentOdometerKm),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    "Updated ${Fmt.date(currentCar.lastOdometerUpdateDay)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            FilledTonalButton(onClick = { showOdometer = true }) {
                                Icon(Icons.Default.Speed, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Update km")
                            }
                        }
                    }
                }
            }

            if (services.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No services logged yet.\nTap “Add service” to log the first one.",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Jobs logged on the same day at the same kilometres were one visit: show them together.
                val visits = services.groupBy { it.dateEpochDay to it.odometerKm }.values.toList()
                items(visits, key = { it.first().id }) { visit ->
                    VisitCard(services = visit, onEdit = onEditService)
                }
            }
        }
    }
}

@Composable
private fun VisitCard(services: List<Service>, onEdit: (Long) -> Unit) {
    val first = services.first()
    // One note shared by all jobs is shown once under the header; different notes go with their job.
    val sharedNotes = first.notes.takeIf { n -> n.isNotBlank() && services.all { it.notes == n } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                "${Fmt.date(first.dateEpochDay)} · ${Fmt.km(first.odometerKm)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (sharedNotes != null) {
                Text(
                    sharedNotes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            services.forEach { service ->
                JobRow(service = service, showNotes = sharedNotes == null, onClick = { onEdit(service.id) })
            }
        }
    }
}

@Composable
private fun JobRow(service: Service, showNotes: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(service.jobName, style = MaterialTheme.typography.titleMedium)

            val repeat = listOfNotNull(
                service.intervalKm?.let { "every ${Fmt.km(it)}" },
                service.intervalMonths?.let { "every $it months" }
            ).joinToString(" or ")
            if (repeat.isNotEmpty()) {
                Text(
                    "Repeat $repeat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showNotes && service.notes.isNotBlank()) {
                Text(
                    service.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Edit ${service.jobName}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OdometerDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update odometer") },
        text = {
            NumberField(
                value = text,
                onChange = { text = it },
                label = "Kilometres",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value != null) onSave(value) },
                enabled = value != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

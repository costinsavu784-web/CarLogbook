package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
                items(services, key = { it.id }) { service ->
                    ServiceCard(service = service, onClick = { onEditService(service.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceCard(service: Service, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(service.jobName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${Fmt.date(service.dateEpochDay)} · ${Fmt.km(service.odometerKm)}",
                style = MaterialTheme.typography.bodyMedium
            )

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

            if (service.notes.isNotBlank()) {
                Text(
                    service.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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

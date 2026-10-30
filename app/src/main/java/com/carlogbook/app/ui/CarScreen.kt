package com.carlogbook.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.R
import com.carlogbook.app.data.Car
import com.carlogbook.app.data.LastCar
import com.carlogbook.app.data.Service
import com.carlogbook.app.data.ServicePart
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.CategoryStatus
import com.carlogbook.app.domain.Fmt
import com.carlogbook.app.notify.ReminderScheduler

private const val TAB_OVERVIEW = 0
private const val TAB_HISTORY = 1

/**
 * One car: an Overview tab with the odometer and the life left on each component,
 * and a History tab with every service logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(
    vm: MainViewModel,
    carId: Long,
    onBack: () -> Unit,
    onAddService: () -> Unit,
    onEditService: (Long) -> Unit,
    onEditCar: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(carId) { LastCar.set(context, carId) }

    val carFlow = remember(carId) { vm.car(carId) }
    val servicesFlow = remember(carId) { vm.services(carId) }
    val upcomingFlow = remember(carId) { vm.upcoming(carId) }
    val car by carFlow.collectAsStateWithLifecycle(initialValue = null)
    val services by servicesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val upcoming by upcomingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val placements by vm.placements.collectAsStateWithLifecycle()
    val partsFlow = remember(carId) { vm.parts(carId) }
    val parts by partsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val partsByService = remember(parts) { parts.groupBy { it.serviceId } }
    val currentCar = car
    val categories = remember(upcoming, services, placements) { Catalog.status(upcoming, services, placements) }

    var tab by rememberSaveable { mutableIntStateOf(TAB_OVERVIEW) }

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

    val checkingToast = stringResource(R.string.checking_reminders)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(currentCar?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            ReminderScheduler.runNow(context)
                            Toast.makeText(context, checkingToast, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = stringResource(R.string.send_reminders_now))
                        }
                        IconButton(onClick = onEditCar) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_car))
                        }
                    }
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == TAB_OVERVIEW, onClick = { tab = TAB_OVERVIEW }, text = { Text(stringResource(R.string.overview)) })
                    Tab(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, text = { Text(stringResource(R.string.history)) })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddService,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_service)) }
            )
        }
    ) { padding ->
        when (tab) {
            TAB_OVERVIEW -> OverviewTab(
                car = currentCar,
                categories = categories,
                hasServices = services.isNotEmpty(),
                onUpdateOdometer = { showOdometer = true },
                onOpenCategory = onOpenCategory,
                modifier = Modifier.padding(padding)
            )
            else -> HistoryTab(
                services = services,
                partsByService = partsByService,
                onEditService = onEditService,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overview tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun OverviewTab(
    car: Car?,
    categories: List<CategoryStatus>,
    hasServices: Boolean,
    onUpdateOdometer: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (car != null) {
            item(key = "odometer") { OdometerCard(car, onUpdateOdometer) }
        }

        item(key = "title") {
            Text(
                stringResource(R.string.components),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!hasServices) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.overview_hint_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(categories, key = { it.category.id }) { cat ->
            CategoryCard(cat, onClick = { onOpenCategory(cat.category.id) })
        }
    }
}

/** One category: name, a colour dot for its worst component, and a one-line summary. */
@Composable
private fun CategoryCard(cat: CategoryStatus, onClick: () -> Unit) {
    val worst = cat.worst
    val color = statusColor(worst)
    val knownCount = cat.known.size
    val total = cat.components.size
    val unknownCount = cat.unknown.size
    val wearCount = cat.wearParts.size

    val summary = when {
        wearCount == 0 -> stringResource(R.string.cat_summary_no_wear, total)
        knownCount == 0 -> stringResource(R.string.cat_summary_all_unknown, wearCount)
        else -> {
            val urgent = cat.mostUrgent!!
            val head = "${urgent.component.displayName}: ${lifeHeadline(urgent.upcoming!!).lowercase()}"
            if (unknownCount > 0) "$head · ${stringResource(R.string.cat_summary_unknown_suffix, unknownCount)}" else head
        }
    }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (worst == null) color.copy(alpha = 0.3f) else color)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cat.category.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (worst == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (wearCount > 0) {
                Text(
                    "$knownCount/$wearCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OdometerCard(car: Car, onUpdate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.current_odometer), style = MaterialTheme.typography.labelMedium)
                Text(Fmt.km(car.currentOdometerKm), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.updated_on, Fmt.date(car.lastOdometerUpdateDay)), style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onUpdate) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_km))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// History tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun HistoryTab(
    services: List<Service>,
    partsByService: Map<Long, List<ServicePart>>,
    onEditService: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (services.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_services_yet), textAlign = TextAlign.Center)
        }
        return
    }

    // Jobs logged on the same day at the same kilometres were one visit: show them together.
    val visits = services.groupBy { it.dateEpochDay to it.odometerKm }.values.toList()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(visits, key = { it.first().id }) { visit ->
            VisitCard(services = visit, partsByService = partsByService, onEdit = onEditService)
        }
    }
}

@Composable
private fun VisitCard(
    services: List<Service>,
    partsByService: Map<Long, List<ServicePart>>,
    onEdit: (Long) -> Unit
) {
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
                JobRow(
                    service = service,
                    parts = partsByService[service.id] ?: emptyList(),
                    showNotes = sharedNotes == null,
                    onClick = { onEdit(service.id) }
                )
            }
        }
    }
}

@Composable
private fun JobRow(service: Service, parts: List<ServicePart>, showNotes: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(jobLabel(service.jobName), style = MaterialTheme.typography.titleMedium)

            val repeat = repeatText(service)
            if (repeat.isNotEmpty()) {
                Text(repeat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            PartsSummary(parts)

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
            contentDescription = stringResource(R.string.edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Oil filter · 1 · OEM 11 42 8 583 898 · Mann HU 8007 z", skipping blanks. */
@Composable
fun partLine(name: String, quantity: String, oem: String, supplier: String): String =
    listOfNotNull(
        name.ifBlank { null },
        quantity.ifBlank { null },
        oem.ifBlank { null }?.let { stringResource(R.string.oem_prefix, it) },
        supplier.ifBlank { null }
    ).joinToString(" · ")

/** Parts used, one per line. */
@Composable
fun PartsSummary(parts: List<ServicePart>) {
    if (parts.isEmpty()) return
    Column {
        parts.forEach { p ->
            Text(
                partLine(p.name, p.quantity, p.oemNumber, p.supplierNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OdometerDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_odometer)) },
        text = {
            NumberField(
                value = text,
                onChange = { text = it },
                label = stringResource(R.string.kilometres),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value != null) onSave(value) },
                enabled = value != null
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

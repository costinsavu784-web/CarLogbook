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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.R
import com.carlogbook.app.data.MountedPart
import com.carlogbook.app.data.Service
import com.carlogbook.app.data.ServicePart
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Component
import com.carlogbook.app.domain.ComponentStatus
import com.carlogbook.app.domain.Fmt

/**
 * One component on one car: where it stands today, what is mounted, a few numbers about how
 * often it has been done, and every service ever logged for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentScreen(
    vm: MainViewModel,
    carId: Long,
    componentName: String,
    onBack: () -> Unit,
    onAddService: () -> Unit,
    onEditService: (Long) -> Unit
) {
    val upcomingFlow = remember(carId) { vm.upcoming(carId) }
    val servicesFlow = remember(carId) { vm.services(carId) }
    val upcoming by upcomingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val allServices by servicesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val placements by vm.placements.collectAsStateWithLifecycle()
    val partsFlow = remember(carId) { vm.parts(carId) }
    val parts by partsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val partsByService = remember(parts) { parts.groupBy { it.serviceId } }

    val status: ComponentStatus = remember(upcoming, allServices, placements, componentName) {
        Catalog.status(upcoming, allServices, placements)
            .flatMap { it.components }
            .firstOrNull { it.component.matches(componentName) }
            ?: ComponentStatus(Component(componentName), null, null)
    }
    val component = status.component

    // Newest first, as the DAO already orders them.
    val history = remember(allServices, component) { allServices.filter { component.matches(it.jobName) } }

    // What is on the car now: the user's own list if they set one, otherwise the last service's parts.
    val componentKey = remember(component) { Catalog.componentKey(component.name) }
    val mountedFlow = remember(carId) { vm.mounted(carId) }
    val allMounted by mountedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val mountedOverride = remember(allMounted, componentKey) { allMounted.filter { it.componentKey == componentKey } }
    val lastServiceWithParts = remember(history, partsByService) {
        history.firstOrNull { partsByService[it.id]?.isNotEmpty() == true }
    }
    val lastServiceParts = remember(lastServiceWithParts, partsByService) {
        lastServiceWithParts?.let { partsByService[it.id] } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(component.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddService,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_service)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") { ComponentCard(status = status) }

            item(key = "mounted") {
                MountedCard(
                    userSet = mountedOverride,
                    fromLastService = lastServiceParts,
                    lastService = lastServiceWithParts,
                    onSave = { list -> vm.setMounted(carId, component.name, list) }
                )
            }

            if (history.isNotEmpty()) {
                item(key = "stats") { StatsCard(history) }
            }

            item(key = "history-title") {
                Text(
                    stringResource(R.string.history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (history.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.component_history_empty), textAlign = TextAlign.Center)
                    }
                }
            } else {
                itemsIndexed(history, key = { _, s -> s.id }) { index, service ->
                    val previous = history.getOrNull(index + 1)
                    HistoryRow(
                        service = service,
                        parts = partsByService[service.id] ?: emptyList(),
                        previous = previous,
                        onClick = { onEditService(service.id) }
                    )
                }
            }
        }
    }
}

/** A part as typed in the mounted editor. */
private data class MountedEntry(
    val name: String = "",
    val quantity: String = "",
    val oem: String = "",
    val supplier: String = ""
) {
    val isBlank: Boolean get() = name.isBlank() && quantity.isBlank() && oem.isBlank() && supplier.isBlank()
}

@Composable
private fun MountedCard(
    userSet: List<MountedPart>,
    fromLastService: List<ServicePart>,
    lastService: Service?,
    onSave: (List<MountedPart>) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<MountedEntry>() }
    val defaultPartName = stringResource(R.string.part_default_name)

    val isUserSet = userSet.isNotEmpty()
    val shown: List<String> = if (isUserSet) {
        userSet.map { partLine(it.name, it.quantity, it.oemNumber, it.supplierNumber) }
    } else {
        fromLastService.map { partLine(it.name, it.quantity, it.oemNumber, it.supplierNumber) }
    }

    fun startEditing() {
        entries.clear()
        if (isUserSet) {
            entries.addAll(userSet.map { MountedEntry(it.name, it.quantity, it.oemNumber, it.supplierNumber) })
        } else {
            entries.addAll(fromLastService.map { MountedEntry(it.name, it.quantity, it.oemNumber, it.supplierNumber) })
        }
        if (entries.isEmpty()) entries.add(MountedEntry())
        editing = true
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.currently_mounted), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            editing -> stringResource(R.string.mounted_editing)
                            isUserSet -> stringResource(R.string.mounted_user_set)
                            lastService != null && fromLastService.isNotEmpty() ->
                                stringResource(R.string.mounted_from_service, Fmt.date(lastService.dateEpochDay))
                            else -> stringResource(R.string.mounted_unknown)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!editing) {
                    TextButton(onClick = { startEditing() }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.edit))
                    }
                }
            }

            if (!editing) {
                shown.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
            } else {
                entries.forEachIndexed { index, e ->
                    MountedEditor(
                        entry = e,
                        onChange = { entries[index] = it },
                        onRemove = { entries.removeAt(index) }
                    )
                }
                TextButton(onClick = { entries.add(MountedEntry()) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_part))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (isUserSet) {
                        TextButton(onClick = {
                            onSave(emptyList())
                            editing = false
                        }) { Text(stringResource(R.string.use_last_service)) }
                    }
                    TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        onSave(
                            entries.filter { !it.isBlank }.map {
                                MountedPart(
                                    carId = 0, componentKey = "",
                                    name = it.name.trim().ifEmpty { defaultPartName },
                                    quantity = it.quantity.trim(),
                                    oemNumber = it.oem.trim(),
                                    supplierNumber = it.supplier.trim()
                                )
                            }
                        )
                        editing = false
                    }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun MountedEditor(entry: MountedEntry, onChange: (MountedEntry) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = entry.name,
                onValueChange = { onChange(entry.copy(name = it)) },
                label = { Text(stringResource(R.string.part_or_material)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_part))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry.quantity,
                onValueChange = { onChange(entry.copy(quantity = it)) },
                label = { Text(stringResource(R.string.quantity)) },
                singleLine = true,
                modifier = Modifier.weight(0.6f)
            )
            OutlinedTextField(
                value = entry.oem,
                onValueChange = { onChange(entry.copy(oem = it)) },
                label = { Text(stringResource(R.string.oem_number)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = entry.supplier,
            onValueChange = { onChange(entry.copy(supplier = it)) },
            label = { Text(stringResource(R.string.supplier_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
    }
}

/** How many times, and on average how far apart, this component has been serviced. */
@Composable
private fun StatsCard(history: List<Service>) {
    val sorted = history.sortedWith(compareBy({ it.dateEpochDay }, { it.odometerKm }))
    val first = sorted.first()
    val last = sorted.last()
    val gaps = sorted.size - 1

    val avgKm = if (gaps > 0) (last.odometerKm - first.odometerKm) / gaps else null
    val avgDays = if (gaps > 0) (last.dateEpochDay - first.dateEpochDay) / gaps else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Stat(stringResource(R.string.times_done), sorted.size.toString())
            Stat(stringResource(R.string.avg_every), avgKm?.takeIf { it > 0 }?.let { Fmt.km(it) } ?: "—")
            Stat(stringResource(R.string.avg_every), avgDays?.takeIf { it > 0 }?.let { Fmt.span(it) } ?: "—")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRow(service: Service, parts: List<ServicePart>, previous: Service?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${Fmt.date(service.dateEpochDay)} · ${Fmt.km(service.odometerKm)}",
                    style = MaterialTheme.typography.titleSmall
                )

                if (previous != null) {
                    val km = service.odometerKm - previous.odometerKm
                    val days = service.dateEpochDay - previous.dateEpochDay
                    val gap = listOfNotNull(
                        km.takeIf { it > 0 }?.let { Fmt.km(it) },
                        days.takeIf { it > 0 }?.let { Fmt.span(it) }
                    )
                    if (gap.isNotEmpty()) {
                        Text(
                            stringResource(R.string.after_previous, gap.joinToString(" ${stringResource(R.string.and)} ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val repeat = repeatText(service)
                if (repeat.isNotEmpty()) {
                    Text(repeat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                PartsSummary(parts)

                if (service.notes.isNotBlank()) {
                    Text(service.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

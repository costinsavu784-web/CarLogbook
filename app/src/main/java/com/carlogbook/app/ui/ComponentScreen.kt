package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Component
import com.carlogbook.app.domain.ComponentStatus
import com.carlogbook.app.domain.Fmt

/**
 * One component on one car: where it stands today, a few numbers about how often it has been
 * done, and every service ever logged for it.
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

    val status: ComponentStatus = remember(upcoming, allServices, placements, componentName) {
        Catalog.status(upcoming, allServices, placements)
            .flatMap { it.components }
            .firstOrNull { it.component.matches(componentName) }
            ?: ComponentStatus(Component(componentName), null, null)
    }
    val component = status.component

    // Newest first, as the DAO already orders them.
    val history = remember(allServices, component) { allServices.filter { component.matches(it.jobName) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(component.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") { ComponentCard(status = status) }

            if (history.size >= 1) {
                item(key = "stats") { StatsCard(history) }
            }

            item(key = "history-title") {
                Text(
                    "History",
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
                        Text(
                            "Nothing logged for this component yet.\nTap “Add service” to record the first one.",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(history, key = { _, s -> s.id }) { index, service ->
                    // Distance and time since the previous time this was done (the next, older entry).
                    val previous = history.getOrNull(index + 1)
                    HistoryRow(service = service, previous = previous, onClick = { onEditService(service.id) })
                }
            }
        }
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
            Stat("Times done", sorted.size.toString())
            Stat("Avg. every", avgKm?.takeIf { it > 0 }?.let { Fmt.km(it) } ?: "—")
            Stat("Avg. every", avgDays?.takeIf { it > 0 }?.let { Fmt.span(it) } ?: "—")
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
private fun HistoryRow(service: Service, previous: Service?, onClick: () -> Unit) {
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
                    val parts = listOfNotNull(
                        km.takeIf { it > 0 }?.let { Fmt.km(it) },
                        days.takeIf { it > 0 }?.let { Fmt.span(it) }
                    )
                    if (parts.isNotEmpty()) {
                        Text(
                            "${parts.joinToString(" and ")} after the previous one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val repeat = listOfNotNull(
                    service.intervalKm?.let { "every ${Fmt.km(it)}" },
                    service.intervalMonths?.let { "every $it months" }
                ).joinToString(" or ")
                if (repeat.isNotEmpty()) {
                    Text("Repeat $repeat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (service.notes.isNotBlank()) {
                    Text(service.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

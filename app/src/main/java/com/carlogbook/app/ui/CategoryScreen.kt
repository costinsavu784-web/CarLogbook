package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.ComponentStatus

/**
 * Every component in one category for one car, each with its life or "Life unknown".
 * Components can be moved to another category or added here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    vm: MainViewModel,
    carId: Long,
    categoryId: String,
    onBack: () -> Unit,
    onOpenComponent: (String) -> Unit
) {
    val upcomingFlow = remember(carId) { vm.upcoming(carId) }
    val servicesFlow = remember(carId) { vm.services(carId) }
    val upcoming by upcomingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val services by servicesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val placements by vm.placements.collectAsStateWithLifecycle()

    val statuses = remember(upcoming, services, placements) { Catalog.status(upcoming, services, placements) }
    val category = statuses.firstOrNull { it.category.id == categoryId }
    val isOther = categoryId == Catalog.OTHER_ID

    var moving by remember { mutableStateOf<ComponentStatus?>(null) }
    var adding by remember { mutableStateOf(false) }

    moving?.let { status ->
        val name = status.component.name
        val isMoved = placements.any { it.key == name.lowercase() && it.categoryId.isNotEmpty() }
        val builtInHome = Catalog.homeCategoryOf(name)
        MoveDialog(
            componentName = name,
            currentCategoryId = categoryId,
            canReset = isMoved,
            resetLabel = builtInHome?.let { "Back to ${it.name}" } ?: "Back to Other",
            onMove = { target ->
                vm.placeComponent(name, target)
                moving = null
            },
            onReset = {
                vm.resetPlacement(name)
                moving = null
            },
            onDismiss = { moving = null }
        )
    }

    if (adding) {
        AddComponentDialog(
            categoryName = category?.category?.name ?: "",
            existingNames = statuses.flatMap { s -> s.components.map { it.component.name } },
            onAdd = { name, wears ->
                vm.placeComponent(name, categoryId, wears)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.category?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isOther) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add component") }
                )
            }
        }
    ) { padding ->
        if (category == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hint") {
                Text(
                    if (isOther) {
                        "Jobs that are not in any category. Tap one to see its history, or use ⋮ to file it where you want it."
                    } else {
                        "Tap a component to see its history and log a service. Use ⋮ to move it or change its type."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Tracked parts first (most urgent on top), then unknown wear parts, then replace-when-faulty ones.
            val ordered = category.components.sortedWith(
                compareBy<ComponentStatus>({ !it.isKnown }, { !it.component.wears }, { it.upcoming?.lifeLeft ?: 1f })
            )
            items(ordered, key = { it.component.name.lowercase() }) { status ->
                ComponentCard(
                    status = status,
                    onClick = { onOpenComponent(status.component.name) },
                    trailing = {
                        ComponentMenu(
                            wears = status.component.wears,
                            onMove = { moving = status },
                            onToggleWears = { vm.setComponentWears(status.component.name, !status.component.wears) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ComponentMenu(wears: Boolean, onMove: () -> Unit, onToggleWears: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Move to another category") },
            onClick = {
                open = false
                onMove()
            }
        )
        DropdownMenuItem(
            text = { Text(if (wears) "Mark as “replace when faulty”" else "Mark as wear part") },
            onClick = {
                open = false
                onToggleWears()
            }
        )
    }
}

@Composable
private fun MoveDialog(
    componentName: String,
    currentCategoryId: String,
    canReset: Boolean,
    resetLabel: String,
    onMove: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentCategoryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move “$componentName”") },
        text = {
            Column {
                Catalog.categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == cat.id, onClick = { selected = cat.id })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == cat.id, onClick = { selected = cat.id })
                        Text(cat.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(selected) },
                enabled = selected != currentCategoryId
            ) { Text("Move") }
        },
        dismissButton = {
            Row {
                if (canReset) {
                    TextButton(onClick = onReset) { Text(resetLabel) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun AddComponentDialog(
    categoryName: String,
    existingNames: List<String>,
    onAdd: (name: String, wears: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var wears by remember { mutableStateOf(true) }
    val clean = name.trim()
    val duplicate = existingNames.any { it.equals(clean, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add component to $categoryName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Component name") },
                    placeholder = { Text("For example: Intercooler") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("A component with this name already exists.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wears out", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (wears) "Used up over km or time, like oil or pads. Shows a life bar."
                            else "Works until it fails, like a sensor. No life bar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = wears, onCheckedChange = { wears = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(clean, wears) }, enabled = clean.isNotEmpty() && !duplicate) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

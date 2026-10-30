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
    onLogComponent: (String) -> Unit
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
        val isPlaced = placements.any { it.key == name.lowercase() }
        val builtInHome = Catalog.builtInComponentFor(name)?.let { comp ->
            Catalog.categories.firstOrNull { cat -> cat.components.any { it.name == comp.name } }
        }
        MoveDialog(
            componentName = name,
            currentCategoryId = categoryId,
            canReset = isPlaced,
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
            onAdd = { name ->
                vm.placeComponent(name, categoryId)
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
                        "Jobs that are not in any category. Use ⋮ to file one where you want it."
                    } else {
                        "Tap a component to log a service for it. Use ⋮ to move it to another category."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Known components first, most urgent on top; unknown ones keep catalogue order.
            val ordered = category.components.sortedWith(
                compareBy<ComponentStatus> { !it.isKnown }.thenBy { it.upcoming?.lifeLeft ?: 1f }
            )
            items(ordered, key = { it.component.name.lowercase() }) { status ->
                ComponentCard(
                    status = status,
                    onClick = { onLogComponent(status.component.name) },
                    trailing = { ComponentMenu(onMove = { moving = status }) }
                )
            }
        }
    }
}

@Composable
private fun ComponentMenu(onMove: () -> Unit) {
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
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                Text(
                    "It will show as “Life unknown” until you log a service for it with a reminder interval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(clean) }, enabled = clean.isNotEmpty() && !duplicate) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

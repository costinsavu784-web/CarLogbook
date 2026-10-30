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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.R
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.ComponentStatus

/**
 * Every component in one category for one car, each with its life or "Life unknown".
 * Components can be moved to another category, retyped, or added here.
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
            componentLabel = status.component.displayName,
            currentCategoryId = categoryId,
            canReset = isMoved,
            resetLabel = stringResource(R.string.back_to_category, builtInHome?.displayName ?: stringResource(R.string.cat_other)),
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
            categoryName = category?.category?.displayName ?: "",
            existingNames = statuses.flatMap { s -> s.components.flatMap { listOf(it.component.name, it.component.displayName) } },
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
                title = { Text(category?.category?.displayName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isOther) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_component)) }
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
                    stringResource(if (isOther) R.string.category_hint_other else R.string.category_hint),
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
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move_to_category)) },
            onClick = {
                open = false
                onMove()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (wears) R.string.mark_fail_only else R.string.mark_wear)) },
            onClick = {
                open = false
                onToggleWears()
            }
        )
    }
}

@Composable
private fun MoveDialog(
    componentLabel: String,
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
        title = { Text(stringResource(R.string.move_title, componentLabel)) },
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
                        Text(cat.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(selected) },
                enabled = selected != currentCategoryId
            ) { Text(stringResource(R.string.move)) }
        },
        dismissButton = {
            Row {
                if (canReset) {
                    TextButton(onClick = onReset) { Text(resetLabel) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
        title = { Text(stringResource(R.string.add_component_to, categoryName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.component_name)) },
                    placeholder = { Text(stringResource(R.string.component_name_hint)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text(stringResource(R.string.component_exists)) }
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
                        Text(stringResource(R.string.wears_out), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (wears) R.string.wears_out_yes else R.string.wears_out_no),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = wears, onCheckedChange = { wears = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(clean, wears) }, enabled = clean.isNotEmpty() && !duplicate) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

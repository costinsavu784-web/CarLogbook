package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.domain.Catalog

/** Every component in one category for one car, each with its life or "Life unknown". */
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

    val statuses = remember(upcoming, services) { Catalog.status(upcoming, services) }
    val category = statuses.firstOrNull { it.category.id == categoryId }

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
        }
    ) { padding ->
        if (category == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hint") {
                Text(
                    "Tap a component to log a service for it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Known components first, most urgent on top; unknown ones keep catalogue order.
            val ordered = category.components.sortedWith(
                compareBy<com.carlogbook.app.domain.ComponentStatus> { !it.isKnown }
                    .thenBy { it.upcoming?.lifeLeft ?: 1f }
            )
            items(ordered, key = { it.component.name.lowercase() }) { status ->
                ComponentCard(status = status, onClick = { onLogComponent(status.component.name) })
            }
        }
    }
}

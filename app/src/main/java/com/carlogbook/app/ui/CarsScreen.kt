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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carlogbook.app.data.Car
import com.carlogbook.app.domain.Fmt
import com.carlogbook.app.update.UpdateCard
import com.carlogbook.app.update.UpdateState
import com.carlogbook.app.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarsScreen(
    vm: MainViewModel,
    onOpenCar: (Long) -> Unit,
    onAddCar: () -> Unit,
    onEditCar: (Long) -> Unit
) {
    val cars by vm.cars.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updateVm.autoCheck() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My garage") },
                actions = {
                    IconButton(onClick = updateVm::checkNow, enabled = updateState !is UpdateState.Checking) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "Check for updates")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCar,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add car") }
            )
        }
    ) { padding ->
        if (cars.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                UpdateCard(updateState, updateVm, Modifier.padding(16.dp))
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No cars yet.\nTap “Add car” to start your logbook.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (updateState !is UpdateState.Idle) {
                    item(key = "update") { UpdateCard(updateState, updateVm) }
                }
                items(cars, key = { it.id }) { car ->
                    CarCard(
                        car = car,
                        onOpen = { onOpenCar(car.id) },
                        onEdit = { onEditCar(car.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarCard(car: Car, onOpen: () -> Unit, onEdit: () -> Unit) {
    val subtitle = listOfNotNull(
        car.make.ifBlank { null },
        car.model.ifBlank { null },
        car.year?.toString()
    ).joinToString(" ")

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(car.name, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    Fmt.km(car.currentOdometerKm),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit car")
            }
        }
    }
}

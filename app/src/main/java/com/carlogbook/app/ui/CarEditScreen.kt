package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlogbook.app.data.Car
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarEditScreen(
    vm: MainViewModel,
    carId: Long,
    onDone: () -> Unit,
    onDeleted: () -> Unit
) {
    val isNew = carId == 0L

    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var odometer by rememberSaveable { mutableStateOf("") }
    var warnKm by rememberSaveable { mutableStateOf("1000") }
    var warnDays by rememberSaveable { mutableStateOf("14") }

    var existing by remember { mutableStateOf<Car?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(carId) {
        if (!isNew) {
            val car = vm.getCar(carId)
            existing = car
            if (car != null && !loaded) {
                name = car.name
                make = car.make
                model = car.model
                year = car.year?.toString() ?: ""
                odometer = car.currentOdometerKm.toString()
                warnKm = car.warnBeforeKm.toString()
                warnDays = car.warnBeforeDays.toString()
                loaded = true
            }
        }
    }

    val odometerValue = odometer.toIntOrNull() ?: 0
    val warnKmValue = warnKm.toIntOrNull() ?: 1000
    val warnDaysValue = warnDays.toIntOrNull() ?: 14
    val canSave = name.isNotBlank() && (isNew || existing != null)

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this car?",
            message = "The car and its whole service history will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteCar(carId)
                showDelete = false
                onDeleted()
            },
            onDismiss = { showDelete = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add car" else "Edit car") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("For example: My BMW 320d") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = make,
                    onValueChange = { make = it },
                    label = { Text("Make") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = year,
                    onChange = { year = it },
                    label = "Year",
                    maxLength = 4,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = odometer,
                    onChange = { odometer = it },
                    label = "Odometer (km)",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "Warn me before a service is due",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = warnKm,
                    onChange = { warnKm = it },
                    label = "km before",
                    maxLength = 5,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = warnDays,
                    onChange = { warnDays = it },
                    label = "days before",
                    maxLength = 3,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val today = LocalDate.now().toEpochDay()
                    val base = existing
                    val car = if (base == null) {
                        Car(
                            name = name.trim(),
                            make = make.trim(),
                            model = model.trim(),
                            year = year.toIntOrNull(),
                            currentOdometerKm = odometerValue,
                            lastOdometerUpdateDay = today,
                            warnBeforeKm = warnKmValue,
                            warnBeforeDays = warnDaysValue
                        )
                    } else {
                        base.copy(
                            name = name.trim(),
                            make = make.trim(),
                            model = model.trim(),
                            year = year.toIntOrNull(),
                            currentOdometerKm = odometerValue,
                            lastOdometerUpdateDay =
                                if (odometerValue != base.currentOdometerKm) today else base.lastOdometerUpdateDay,
                            warnBeforeKm = warnKmValue,
                            warnBeforeDays = warnDaysValue
                        )
                    }
                    vm.saveCar(car)
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) { Text("Save car") }

            if (!isNew) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete this car", color = MaterialTheme.colorScheme.error) }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

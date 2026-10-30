package com.carlogbook.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlogbook.app.domain.Fmt
import com.carlogbook.app.domain.ReminderStatus
import com.carlogbook.app.domain.Upcoming
import com.carlogbook.app.notify.ReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(vm: MainViewModel, carId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val flow = remember(carId) { vm.upcoming(carId) }
    val reminders by flow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ReminderScheduler.runNow(context)
                        Toast.makeText(context, "Checking reminders…", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check reminders now")
                    }
                }
            )
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No reminders yet.\nWhen you add a service, fill in “Every … km” or “Every … months” to get one.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders, key = { it.jobName.lowercase() }) { reminder ->
                    UpcomingCard(reminder)
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(item: Upcoming) {
    val container = when (item.status) {
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        ReminderStatus.DUE_SOON -> MaterialTheme.colorScheme.tertiaryContainer
        ReminderStatus.OK -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when (item.status) {
        ReminderStatus.OVERDUE -> "Overdue"
        ReminderStatus.DUE_SOON -> "Due soon"
        ReminderStatus.OK -> "On track"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(item.jobName, style = MaterialTheme.typography.titleMedium)
            Text(
                Fmt.describe(item).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

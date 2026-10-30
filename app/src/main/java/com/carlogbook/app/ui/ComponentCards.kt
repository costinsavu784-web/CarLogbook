package com.carlogbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carlogbook.app.domain.ComponentStatus
import com.carlogbook.app.domain.Fmt
import com.carlogbook.app.domain.ReminderStatus
import com.carlogbook.app.domain.Upcoming

val DueSoonColor = Color(0xFFE59400)

@Composable
fun statusColor(status: ReminderStatus?): Color = when (status) {
    ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.error
    ReminderStatus.DUE_SOON -> DueSoonColor
    ReminderStatus.OK -> MaterialTheme.colorScheme.primary
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * One component: its life bar when we know it, otherwise "Life unknown".
 * [trailing] is an optional control shown at the end of the title row (for example a menu).
 */
@Composable
fun ComponentCard(
    status: ComponentStatus,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val upcoming = status.upcoming
    if (upcoming != null) {
        KnownComponentCard(status.component.name, upcoming, onClick, trailing)
    } else {
        UnknownComponentCard(status, onClick, trailing)
    }
}

@Composable
private fun KnownComponentCard(
    name: String,
    item: Upcoming,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    val barColor = statusColor(item.status)
    val container = when (item.status) {
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    CardShell(container, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(lifeHeadline(item), style = MaterialTheme.typography.titleMedium, color = barColor)
            trailing?.invoke()
        }
        LinearProgressIndicator(
            progress = { item.lifeLeft },
            color = barColor,
            trackColor = barColor.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Text(lifeDetails(item), style = MaterialTheme.typography.bodySmall)
        Text(
            "Last done ${Fmt.date(item.lastDoneDay)} at ${Fmt.km(item.lastDoneKm)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnknownComponentCard(
    status: ComponentStatus,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CardShell(MaterialTheme.colorScheme.surface, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.component.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Life unknown", style = MaterialTheme.typography.titleSmall, color = muted)
            trailing?.invoke()
        }
        val last = status.lastService
        Text(
            if (last != null) {
                "Last done ${Fmt.date(last.dateEpochDay)} at ${Fmt.km(last.odometerKm)}, no reminder interval set."
            } else {
                "Never logged. Add a service with “Every … km” or “Every … months” to track it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
    }
}

@Composable
private fun CardShell(container: Color, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        Card(modifier = Modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

/** The one number that matters most: whichever of km or time runs out first. */
fun lifeHeadline(item: Upcoming): String {
    if (item.status == ReminderStatus.OVERDUE) return "Overdue"
    val km = item.remainingKm
    val days = item.remainingDays
    val useKm = when {
        km == null -> false
        days == null -> true
        else -> (item.kmLifeLeft ?: 1f) <= (item.timeLifeLeft ?: 1f)
    }
    return if (useKm) "${Fmt.km(km!!)} left" else "${Fmt.span(days!!)} left"
}

/** Both limits spelled out, for example "6,000 km left (due at 90,000 km) · 3 months left (due 12 Mar 2027)". */
fun lifeDetails(item: Upcoming): String {
    val parts = mutableListOf<String>()
    val km = item.remainingKm
    val dueKm = item.dueKm
    if (km != null && dueKm != null) {
        parts += if (km <= 0) "overdue by ${Fmt.km(-km)}" else "${Fmt.km(km)} left (due at ${Fmt.km(dueKm)})"
    }
    val days = item.remainingDays
    val dueDay = item.dueDay
    if (days != null && dueDay != null) {
        parts += when {
            days < 0 -> "overdue by ${Fmt.span(-days)}"
            days == 0L -> "due today"
            else -> "${Fmt.span(days)} left (due ${Fmt.date(dueDay)})"
        }
    }
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlogbook.app.R
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
 * One component: its life bar when we know it, otherwise "Life unknown" or "Replace when faulty".
 * [trailing] is an optional control shown at the end of the title row (for example a menu).
 */
@Composable
fun ComponentCard(
    status: ComponentStatus,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val upcoming = status.upcoming
    when {
        upcoming != null -> KnownComponentCard(status.component.displayName, upcoming, onClick, trailing)
        status.component.wears -> UnknownComponentCard(status, onClick, trailing)
        else -> FixedComponentCard(status, onClick, trailing)
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
            stringResource(R.string.last_done_at, Fmt.date(item.lastDoneDay), Fmt.km(item.lastDoneKm)),
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
            Text(status.component.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.life_unknown), style = MaterialTheme.typography.titleSmall, color = muted)
            trailing?.invoke()
        }
        val last = status.lastService
        Text(
            if (last != null) {
                stringResource(R.string.last_done_no_interval, Fmt.date(last.dateEpochDay), Fmt.km(last.odometerKm))
            } else {
                stringResource(R.string.never_logged_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
    }
}

/** A part that works until it fails: no life to measure, just when it was last replaced. */
@Composable
private fun FixedComponentCard(
    status: ComponentStatus,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CardShell(MaterialTheme.colorScheme.surface, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.component.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.replace_when_faulty), style = MaterialTheme.typography.titleSmall, color = muted)
            trailing?.invoke()
        }
        val last = status.lastService
        Text(
            if (last != null) stringResource(R.string.last_replaced_at, Fmt.date(last.dateEpochDay), Fmt.km(last.odometerKm))
            else stringResource(R.string.never_replaced),
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
@Composable
fun lifeHeadline(item: Upcoming): String {
    if (item.status == ReminderStatus.OVERDUE) return stringResource(R.string.overdue)
    val km = item.remainingKm
    val days = item.remainingDays
    val useKm = when {
        km == null -> false
        days == null -> true
        else -> (item.kmLifeLeft ?: 1f) <= (item.timeLifeLeft ?: 1f)
    }
    return stringResource(R.string.km_left, if (useKm) Fmt.km(km!!) else Fmt.span(days!!))
}

/** Both limits spelled out, for example "6,000 km left (due at 90,000 km) · 3 months left (due 12 Mar 2027)". */
@Composable
fun lifeDetails(item: Upcoming): String {
    val parts = mutableListOf<String>()
    val km = item.remainingKm
    val dueKm = item.dueKm
    if (km != null && dueKm != null) {
        parts += if (km <= 0) stringResource(R.string.fmt_overdue_by, Fmt.km(-km))
        else stringResource(R.string.life_km_left_due, Fmt.km(km), Fmt.km(dueKm))
    }
    val days = item.remainingDays
    val dueDay = item.dueDay
    if (days != null && dueDay != null) {
        parts += when {
            days < 0 -> stringResource(R.string.fmt_overdue_by, Fmt.span(-days))
            days == 0L -> stringResource(R.string.fmt_due_today)
            else -> stringResource(R.string.life_time_left_due, Fmt.span(days), Fmt.date(dueDay))
        }
    }
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

package com.carlogbook.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.ui.graphics.vector.ImageVector
import com.carlogbook.app.domain.Catalog

/** One icon per category, keyed by the category id used in the catalogue. */
fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    "engine" -> Icons.Default.PrecisionManufacturing
    "brakes" -> Icons.Default.Album
    "suspension" -> Icons.Default.Compress
    "gearbox" -> Icons.Default.Settings
    "tyres" -> Icons.Default.TireRepair
    "electrical" -> Icons.Default.Bolt
    "climate" -> Icons.Default.AcUnit
    "exhaust" -> Icons.Default.Air
    "body" -> Icons.Default.Description
    Catalog.OTHER_ID -> Icons.Default.MoreHoriz
    else -> Icons.Default.MoreHoriz
}

package com.carlogbook.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.carlogbook.app.R
import com.carlogbook.app.domain.Catalog

/**
 * One icon per category, drawn in the style of dashboard warning lights (res/drawable/ic_cat_*).
 * They are plain white shapes; the caller tints them with the status colour.
 */
@Composable
fun categoryIcon(categoryId: String): Painter = painterResource(
    when (categoryId) {
        "engine" -> R.drawable.ic_cat_engine
        "brakes" -> R.drawable.ic_cat_brakes
        "suspension" -> R.drawable.ic_cat_suspension
        "gearbox" -> R.drawable.ic_cat_gearbox
        "tyres" -> R.drawable.ic_cat_tyres
        "electrical" -> R.drawable.ic_cat_electrical
        "climate" -> R.drawable.ic_cat_climate
        "exhaust" -> R.drawable.ic_cat_exhaust
        "body" -> R.drawable.ic_cat_body
        Catalog.OTHER_ID -> R.drawable.ic_cat_other
        else -> R.drawable.ic_cat_other
    }
)

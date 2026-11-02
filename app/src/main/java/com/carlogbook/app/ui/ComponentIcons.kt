package com.carlogbook.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.carlogbook.app.R
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Component

/**
 * Icon for a component, keyed by its canonical (English) name. Car-specific parts use our own
 * drawables (res/drawable/ic_comp_*), generic things use Material icons. Unknown names get a wrench.
 */
@Composable
fun componentIcon(component: Component): Painter {
    val key = Catalog.builtInComponentFor(component.name)?.name ?: component.name
    customIcon(key)?.let { return painterResource(it) }
    return rememberVectorPainter(
        when (key) {
            "Coolant", "Brake fluid", "Power steering fluid", "Gearbox oil", "Differential oil",
            "Transfer case oil", "Washer fluid" -> Icons.Default.WaterDrop
            "Thermostat" -> Icons.Default.Thermostat
            "Battery" -> Icons.Default.BatteryChargingFull
            "Starter motor" -> Icons.Default.Bolt
            "Headlight bulbs" -> Icons.Default.Lightbulb
            "Key battery" -> Icons.Default.Key
            "A/C refrigerant", "A/C compressor" -> Icons.Default.AcUnit
            "Tyre rotation" -> Icons.Default.Sync
            "Service inspection", "Technical inspection" -> Icons.AutoMirrored.Filled.FactCheck
            "Insurance" -> Icons.Default.Shield
            "Road tax" -> Icons.AutoMirrored.Filled.ReceiptLong
            else -> Icons.Default.Build
        }
    )
}

private fun customIcon(key: String): Int? = when (key) {
    "Engine oil" -> R.drawable.ic_comp_oil
    "Oil filter", "Air filter", "Fuel filter", "Cabin filter", "Diesel particulate filter" -> R.drawable.ic_comp_filter
    "Spark plugs", "Glow plugs" -> R.drawable.ic_comp_plug
    "Timing belt", "Auxiliary belt" -> R.drawable.ic_comp_belt
    "Timing chain" -> R.drawable.ic_comp_chain
    "Water pump" -> R.drawable.ic_comp_pump
    "Turbocharger" -> R.drawable.ic_comp_turbo
    "Engine mounts" -> R.drawable.ic_comp_mount
    "Injectors" -> R.drawable.ic_comp_injector
    "Brake pads (front)", "Brake pads (rear)" -> R.drawable.ic_comp_pad
    "Brake discs (front)", "Brake discs (rear)", "Clutch", "Dual-mass flywheel" -> R.drawable.ic_comp_disc
    "Brake lines" -> R.drawable.ic_comp_pipe
    "Handbrake" -> R.drawable.ic_comp_handbrake
    "Shock absorbers (front)", "Shock absorbers (rear)" -> R.drawable.ic_comp_shock
    "Springs" -> R.drawable.ic_cat_suspension
    "Control arms", "Ball joints", "Tie rod ends", "Anti-roll bar links", "Bushings" -> R.drawable.ic_comp_joint
    "Wheel bearings" -> R.drawable.ic_comp_bearing
    "Wheel alignment" -> R.drawable.ic_comp_align
    "Driveshafts", "CV boots" -> R.drawable.ic_comp_shaft
    "Tyres (front)", "Tyres (rear)", "Spare tyre" -> R.drawable.ic_comp_tyre
    "Wheel balancing" -> R.drawable.ic_comp_balance
    "Alternator" -> R.drawable.ic_comp_alternator
    "Wiper blades" -> R.drawable.ic_comp_wiper
    "Heater" -> R.drawable.ic_comp_heater
    "Exhaust", "Catalytic converter" -> R.drawable.ic_comp_exhaust
    "EGR valve" -> R.drawable.ic_comp_egr
    "Lambda sensor" -> R.drawable.ic_comp_sensor
    "AdBlue" -> R.drawable.ic_comp_adblue
    "Windscreen" -> R.drawable.ic_comp_glass
    "Rust protection" -> R.drawable.ic_comp_rust
    else -> null
}

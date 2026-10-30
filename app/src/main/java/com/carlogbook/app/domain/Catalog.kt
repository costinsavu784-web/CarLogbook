package com.carlogbook.app.domain

import android.content.res.Resources
import com.carlogbook.app.R
import com.carlogbook.app.data.ComponentPlacement
import com.carlogbook.app.data.Service

/**
 * A part or job you can track. [name] is the canonical (English) identifier used in storage keys;
 * [nameRes] gives the translated label shown on screen (0 for user-added components).
 * Aliases are older or alternative spellings that mean the same thing.
 * [wears] is true for things used up over km or time (oil, pads, belts) and false for things that
 * simply work until they fail (sensors, pumps, alternator), which have no "life" to show.
 */
data class Component(
    val name: String,
    val aliases: List<String> = emptyList(),
    val wears: Boolean = true,
    val nameRes: Int = 0
) {
    /** Translated label, or the raw name for user-added components. */
    val displayName: String get() = Catalog.displayName(this)

    /** True if a logged job name refers to this component, in any language we ship. */
    fun matches(jobName: String): Boolean {
        val n = jobName.trim()
        if (name.equals(n, ignoreCase = true)) return true
        if (aliases.any { it.equals(n, ignoreCase = true) }) return true
        return Catalog.canonicalOf(n)?.equals(name, ignoreCase = true) == true
    }
}

data class Category(val id: String, val nameRes: Int, val components: List<Component>) {
    val displayName: String get() = Catalog.str(nameRes)
}

/** What we know about one component on one car. */
data class ComponentStatus(
    val component: Component,
    /** Life calculation, present only when the job has a reminder interval. */
    val upcoming: Upcoming?,
    /** Most recent time this job was logged, whether or not it has an interval. */
    val lastService: Service?
) {
    val isKnown: Boolean get() = upcoming != null
    /** A wear part we have no interval for: the only case that is genuinely "unknown". */
    val isUnknown: Boolean get() = component.wears && upcoming == null
}

data class CategoryStatus(val category: Category, val components: List<ComponentStatus>) {
    val known: List<ComponentStatus> get() = components.filter { it.isKnown }
    val unknown: List<ComponentStatus> get() = components.filter { it.isUnknown }
    val wearParts: List<ComponentStatus> get() = components.filter { it.component.wears }
    val worst: ReminderStatus? get() = known.mapNotNull { it.upcoming?.status }.minByOrNull { it.ordinal }
    /** The component closest to needing attention, for the summary line. */
    val mostUrgent: ComponentStatus? get() = known.minByOrNull { it.upcoming!!.lifeLeft }
}

/** Every part of the car, grouped the way a workshop would. */
object Catalog {

    const val OTHER_ID = "other"

    private fun c(name: String, res: Int, aliases: List<String> = emptyList()) = Component(name, aliases, true, res)
    private fun f(name: String, res: Int, aliases: List<String> = emptyList()) = Component(name, aliases, false, res)

    val categories: List<Category> = listOf(
        Category("engine", R.string.cat_engine, listOf(
            c("Engine oil", R.string.comp_engine_oil, listOf("Oil change", "Oil")),
            c("Oil filter", R.string.comp_oil_filter),
            c("Air filter", R.string.comp_air_filter),
            c("Fuel filter", R.string.comp_fuel_filter),
            c("Spark plugs", R.string.comp_spark_plugs),
            c("Glow plugs", R.string.comp_glow_plugs),
            c("Timing belt", R.string.comp_timing_belt),
            c("Timing chain", R.string.comp_timing_chain),
            c("Auxiliary belt", R.string.comp_aux_belt, listOf("Serpentine belt", "Drive belt")),
            c("Coolant", R.string.comp_coolant, listOf("Antifreeze")),
            f("Thermostat", R.string.comp_thermostat),
            f("Water pump", R.string.comp_water_pump),
            f("Turbocharger", R.string.comp_turbo, listOf("Turbo")),
            f("Engine mounts", R.string.comp_engine_mounts),
            f("Injectors", R.string.comp_injectors)
        )),
        Category("brakes", R.string.cat_brakes, listOf(
            c("Brake pads (front)", R.string.comp_brake_pads_front),
            c("Brake pads (rear)", R.string.comp_brake_pads_rear),
            c("Brake discs (front)", R.string.comp_brake_discs_front, listOf("Brake discs")),
            c("Brake discs (rear)", R.string.comp_brake_discs_rear),
            c("Brake fluid", R.string.comp_brake_fluid),
            f("Brake lines", R.string.comp_brake_lines, listOf("Brake hoses")),
            f("Handbrake", R.string.comp_handbrake, listOf("Parking brake"))
        )),
        Category("suspension", R.string.cat_suspension, listOf(
            c("Shock absorbers (front)", R.string.comp_shocks_front),
            c("Shock absorbers (rear)", R.string.comp_shocks_rear),
            f("Springs", R.string.comp_springs),
            f("Control arms", R.string.comp_control_arms, listOf("Wishbones")),
            f("Ball joints", R.string.comp_ball_joints),
            f("Tie rod ends", R.string.comp_tie_rod_ends, listOf("Track rod ends")),
            f("Anti-roll bar links", R.string.comp_arb_links, listOf("Drop links")),
            f("Bushings", R.string.comp_bushings),
            f("Wheel bearings", R.string.comp_wheel_bearings),
            c("Wheel alignment", R.string.comp_wheel_alignment, listOf("Alignment")),
            c("Power steering fluid", R.string.comp_ps_fluid)
        )),
        Category("gearbox", R.string.cat_gearbox, listOf(
            c("Gearbox oil", R.string.comp_gearbox_oil, listOf("Transmission fluid", "Transmission oil")),
            c("Clutch", R.string.comp_clutch),
            f("Dual-mass flywheel", R.string.comp_dmf, listOf("Flywheel")),
            c("Differential oil", R.string.comp_diff_oil),
            f("Driveshafts", R.string.comp_driveshafts),
            f("CV boots", R.string.comp_cv_boots),
            c("Transfer case oil", R.string.comp_transfer_oil)
        )),
        Category("tyres", R.string.cat_tyres, listOf(
            c("Tyres (front)", R.string.comp_tyres_front, listOf("Tyres", "Tires")),
            c("Tyres (rear)", R.string.comp_tyres_rear),
            c("Tyre rotation", R.string.comp_tyre_rotation, listOf("Tire rotation")),
            c("Wheel balancing", R.string.comp_wheel_balancing),
            f("Spare tyre", R.string.comp_spare_tyre)
        )),
        Category("electrical", R.string.cat_electrical, listOf(
            c("Battery", R.string.comp_battery),
            f("Alternator", R.string.comp_alternator),
            f("Starter motor", R.string.comp_starter),
            f("Headlight bulbs", R.string.comp_bulbs),
            c("Wiper blades", R.string.comp_wipers),
            c("Key battery", R.string.comp_key_battery)
        )),
        Category("climate", R.string.cat_climate, listOf(
            c("Cabin filter", R.string.comp_cabin_filter, listOf("Pollen filter")),
            c("A/C refrigerant", R.string.comp_ac_refrigerant, listOf("A/C recharge", "Air conditioning")),
            f("A/C compressor", R.string.comp_ac_compressor),
            f("Heater", R.string.comp_heater)
        )),
        Category("exhaust", R.string.cat_exhaust, listOf(
            f("Exhaust", R.string.comp_exhaust),
            f("Catalytic converter", R.string.comp_cat),
            f("Diesel particulate filter", R.string.comp_dpf, listOf("DPF")),
            f("EGR valve", R.string.comp_egr),
            f("Lambda sensor", R.string.comp_lambda, listOf("Oxygen sensor")),
            c("AdBlue", R.string.comp_adblue)
        )),
        Category("body", R.string.cat_body, listOf(
            c("Service inspection", R.string.comp_service_inspection, listOf("Inspection", "Service")),
            c("Technical inspection", R.string.comp_technical_inspection, listOf("MOT", "Revisione")),
            c("Insurance", R.string.comp_insurance),
            c("Road tax", R.string.comp_road_tax),
            f("Windscreen", R.string.comp_windscreen),
            c("Washer fluid", R.string.comp_washer_fluid),
            c("Rust protection", R.string.comp_rust_protection)
        ))
    )

    private val builtIn: List<Component> = categories.flatMap { it.components }

    // ---- Language ------------------------------------------------------------------------------

    private var res: Resources? = null
    /** Lower-case translated name (every shipped language) -> canonical component name. */
    private var translated: Map<String, String> = emptyMap()

    /**
     * Points the catalogue at the current language and loads the labels of every shipped language,
     * so a job logged as "Olio motore" still matches "Engine oil" after switching back to English.
     */
    fun attach(current: Resources, allLanguages: List<Resources>) {
        res = current
        val map = mutableMapOf<String, String>()
        for (r in allLanguages) {
            for (comp in builtIn) {
                if (comp.nameRes != 0) map[r.getString(comp.nameRes).lowercase()] = comp.name
            }
        }
        translated = map
    }

    internal fun str(id: Int): String = res?.getString(id) ?: ""

    fun displayName(component: Component): String =
        if (component.nameRes != 0) str(component.nameRes).ifEmpty { component.name } else component.name

    /** The canonical name a translated label refers to, or null if it is not a built-in label. */
    fun canonicalOf(jobName: String): String? = translated[jobName.trim().lowercase()]

    /** Translated labels of every built-in component, for the quick-pick suggestions. */
    val allComponentNames: List<String> get() = builtIn.map { it.displayName }

    // ---- Lookup --------------------------------------------------------------------------------

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    /** The built-in component a job name belongs to, ignoring user placements. */
    fun builtInComponentFor(jobName: String): Component? = builtIn.firstOrNull { it.matches(jobName) }

    /**
     * Stable key for a component across spellings and languages: the canonical name if the job
     * matches a built-in component, otherwise the job name itself. Lower case.
     */
    fun componentKey(jobName: String): String =
        (builtInComponentFor(jobName)?.name ?: jobName.trim()).lowercase()

    /** The category a built-in component ships in, or null for user-added ones. */
    fun homeCategoryOf(name: String): Category? {
        val comp = builtInComponentFor(name) ?: return null
        return categories.firstOrNull { cat -> cat.components.any { it.name == comp.name } }
    }

    /**
     * Builds the categories as the user sees them: the built-in catalogue with the user's
     * placements applied. A placement moves a built-in component (and its aliases) to another
     * category, or adds a component the catalogue does not know.
     */
    fun categoriesWith(placements: List<ComponentPlacement>): List<Category> {
        val byKey = placements.associateBy { it.key }

        val moved = mutableMapOf<String, MutableList<Component>>()
        val kept = categories.associate { cat ->
            cat.id to cat.components.mapNotNull { comp ->
                val placement = placementFor(comp, byKey)
                val applied = comp.copy(wears = placement?.wears ?: comp.wears)
                val target = placement?.categoryId?.takeIf { it.isNotEmpty() }
                if (target == null || target == cat.id) {
                    applied
                } else {
                    moved.getOrPut(target) { mutableListOf() }.add(applied)
                    null
                }
            }
        }

        val custom = placements
            .filter { it.categoryId.isNotEmpty() && builtInComponentFor(it.name) == null }
            .groupBy({ it.categoryId }, { Component(it.name, wears = it.wears ?: true) })

        return categories.map { cat ->
            Category(
                cat.id,
                cat.nameRes,
                kept.getValue(cat.id) + (moved[cat.id] ?: emptyList()) + (custom[cat.id] ?: emptyList())
            )
        }
    }

    /** The placement, if any, for a built-in component: by its canonical name, any alias, or any translation. */
    private fun placementFor(comp: Component, byKey: Map<String, ComponentPlacement>): ComponentPlacement? {
        val names = listOf(comp.name) + comp.aliases + translated.filterValues { it == comp.name }.keys
        return names.firstNotNullOfOrNull { byKey[it.lowercase()] }
    }

    /**
     * Status of every category for one car. Jobs that match no component (built-in or placed)
     * are collected in an "Other" category, which is only present when it is not empty.
     */
    fun status(
        upcoming: List<Upcoming>,
        services: List<Service>,
        placements: List<ComponentPlacement> = emptyList()
    ): List<CategoryStatus> {
        val cats = categoriesWith(placements)
        val allComponents = cats.flatMap { it.components }

        val latestByJob = services
            .groupBy { it.jobName.trim().lowercase() }
            .mapValues { (_, list) -> list.maxWith(compareBy({ it.dateEpochDay }, { it.odometerKm }, { it.id })) }

        fun statusFor(component: Component): ComponentStatus {
            val matchingUpcoming = upcoming.filter { component.matches(it.jobName) }
                .maxWithOrNull(compareBy({ it.lastDoneDay }, { it.lastDoneKm }))
            val lastService = latestByJob.values.filter { component.matches(it.jobName) }
                .maxWithOrNull(compareBy({ it.dateEpochDay }, { it.odometerKm }, { it.id }))
            return ComponentStatus(component, matchingUpcoming, lastService)
        }

        val known = cats.map { cat -> CategoryStatus(cat, cat.components.map(::statusFor)) }

        val byKey = placements.associateBy { it.key }
        val otherNames = latestByJob.values
            .map { it.jobName.trim() }
            .filter { name -> allComponents.none { it.matches(name) } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        if (otherNames.isEmpty()) return known

        val other = Category(
            OTHER_ID, R.string.cat_other,
            otherNames.map { Component(it, wears = byKey[it.lowercase()]?.wears ?: true) }
        )
        return known + CategoryStatus(other, other.components.map(::statusFor))
    }
}

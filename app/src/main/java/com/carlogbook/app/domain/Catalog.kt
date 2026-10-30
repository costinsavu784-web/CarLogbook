package com.carlogbook.app.domain

import com.carlogbook.app.data.ComponentPlacement
import com.carlogbook.app.data.Service

/**
 * A part or job you can track. Aliases are older or alternative names that mean the same thing.
 * [wears] is true for things that are used up over km or time (oil, pads, belts) and false for
 * things that simply work until they fail (sensors, pumps, alternator), which have no "life" to show.
 */
data class Component(
    val name: String,
    val aliases: List<String> = emptyList(),
    val wears: Boolean = true
) {
    fun matches(jobName: String): Boolean {
        val n = jobName.trim()
        return name.equals(n, ignoreCase = true) || aliases.any { it.equals(n, ignoreCase = true) }
    }
}

/** Shorthand for a component that is replaced only when it fails. */
private fun fixed(name: String, aliases: List<String> = emptyList()) = Component(name, aliases, wears = false)

data class Category(val id: String, val name: String, val components: List<Component>)

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

    val categories: List<Category> = listOf(
        Category("engine", "Engine", listOf(
            Component("Engine oil", listOf("Oil change", "Oil")),
            Component("Oil filter"),
            Component("Air filter"),
            Component("Fuel filter"),
            Component("Spark plugs"),
            Component("Glow plugs"),
            Component("Timing belt"),
            Component("Timing chain"),
            Component("Auxiliary belt", listOf("Serpentine belt", "Drive belt")),
            Component("Coolant", listOf("Antifreeze")),
            fixed("Thermostat"),
            fixed("Water pump"),
            fixed("Turbocharger", listOf("Turbo")),
            fixed("Engine mounts"),
            fixed("Injectors")
        )),
        Category("brakes", "Brakes", listOf(
            Component("Brake pads (front)"),
            Component("Brake pads (rear)"),
            Component("Brake discs (front)", listOf("Brake discs")),
            Component("Brake discs (rear)"),
            Component("Brake fluid"),
            fixed("Brake lines", listOf("Brake hoses")),
            fixed("Handbrake", listOf("Parking brake"))
        )),
        Category("suspension", "Suspension & steering", listOf(
            Component("Shock absorbers (front)"),
            Component("Shock absorbers (rear)"),
            fixed("Springs"),
            fixed("Control arms", listOf("Wishbones")),
            fixed("Ball joints"),
            fixed("Tie rod ends", listOf("Track rod ends")),
            fixed("Anti-roll bar links", listOf("Drop links")),
            fixed("Bushings"),
            fixed("Wheel bearings"),
            Component("Wheel alignment", listOf("Alignment")),
            Component("Power steering fluid")
        )),
        Category("gearbox", "Gearbox & drivetrain", listOf(
            Component("Gearbox oil", listOf("Transmission fluid", "Transmission oil")),
            Component("Clutch"),
            fixed("Dual-mass flywheel", listOf("Flywheel")),
            Component("Differential oil"),
            fixed("Driveshafts"),
            fixed("CV boots"),
            Component("Transfer case oil")
        )),
        Category("tyres", "Tyres & wheels", listOf(
            Component("Tyres (front)", listOf("Tyres", "Tires")),
            Component("Tyres (rear)"),
            Component("Tyre rotation", listOf("Tire rotation")),
            Component("Wheel balancing"),
            fixed("Spare tyre")
        )),
        Category("electrical", "Electrical", listOf(
            Component("Battery"),
            fixed("Alternator"),
            fixed("Starter motor"),
            fixed("Headlight bulbs"),
            Component("Wiper blades"),
            Component("Key battery")
        )),
        Category("climate", "Climate & cabin", listOf(
            Component("Cabin filter", listOf("Pollen filter")),
            Component("A/C refrigerant", listOf("A/C recharge", "Air conditioning")),
            fixed("A/C compressor"),
            fixed("Heater")
        )),
        Category("exhaust", "Exhaust & emissions", listOf(
            fixed("Exhaust"),
            fixed("Catalytic converter"),
            fixed("Diesel particulate filter", listOf("DPF")),
            fixed("EGR valve"),
            fixed("Lambda sensor", listOf("Oxygen sensor")),
            Component("AdBlue")
        )),
        Category("body", "Body & paperwork", listOf(
            Component("Service inspection", listOf("Inspection", "Service")),
            Component("Technical inspection", listOf("MOT", "Revisione")),
            Component("Insurance"),
            Component("Road tax"),
            fixed("Windscreen"),
            Component("Washer fluid"),
            Component("Rust protection")
        ))
    )

    /** All catalogue names, for the quick-pick suggestions in the service form. */
    val allComponentNames: List<String> = categories.flatMap { c -> c.components.map { it.name } }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    /** The built-in component a job name belongs to, ignoring user placements. */
    fun builtInComponentFor(jobName: String): Component? =
        categories.asSequence().flatMap { it.components.asSequence() }.firstOrNull { it.matches(jobName) }

    /**
     * Builds the categories as the user sees them: the built-in catalogue with the user's
     * placements applied. A placement moves a built-in component (and its aliases) to another
     * category, or adds a component the catalogue does not know.
     */
    fun categoriesWith(placements: List<ComponentPlacement>): List<Category> {
        val byKey = placements.associateBy { it.key }

        // Where does each built-in component end up, and does the user override its wear flag?
        val moved = mutableMapOf<String, MutableList<Component>>() // categoryId -> components
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

        // Components the catalogue does not know, added by the user.
        val custom = placements
            .filter { it.categoryId.isNotEmpty() && builtInComponentFor(it.name) == null }
            .groupBy({ it.categoryId }, { Component(it.name, wears = it.wears ?: true) })

        return categories.map { cat ->
            Category(
                cat.id,
                cat.name,
                kept.getValue(cat.id) + (moved[cat.id] ?: emptyList()) + (custom[cat.id] ?: emptyList())
            )
        }
    }

    /** The placement, if any, for a built-in component: by its name or any alias. */
    private fun placementFor(comp: Component, byKey: Map<String, ComponentPlacement>): ComponentPlacement? =
        (listOf(comp.name) + comp.aliases).firstNotNullOfOrNull { byKey[it.lowercase()] }

    /**
     * Stable key for a component across spellings: the built-in name if the job matches one
     * (so "Oil change" and "Engine oil" share a key), otherwise the job name itself. Lower case.
     */
    fun componentKey(jobName: String): String =
        (builtInComponentFor(jobName)?.name ?: jobName.trim()).lowercase()

    /** The category a built-in component ships in, or null for user-added ones. */
    fun homeCategoryOf(name: String): Category? {
        val comp = builtInComponentFor(name) ?: return null
        return categories.firstOrNull { cat -> cat.components.any { it.name == comp.name } }
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
            OTHER_ID, "Other",
            otherNames.map { Component(it, wears = byKey[it.lowercase()]?.wears ?: true) }
        )
        return known + CategoryStatus(other, other.components.map(::statusFor))
    }
}

package com.carlogbook.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.carlogbook.app.domain.Catalog
import com.carlogbook.app.domain.Fmt
import java.util.Locale

/**
 * The app's language setting. "system" follows the phone; otherwise an ISO code we ship
 * translations for. Applied by wrapping the Activity context, so every screen, dialog and
 * notification text picks up the chosen language without AppCompat.
 */
object AppLocale {
    const val SYSTEM = "system"

    /** Codes we ship string translations for, in menu order. */
    val languages: List<String> = listOf("en", "it")

    private const val PREFS = "settings"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /**
     * Returns a context whose resources use the chosen language, and points the non-Compose
     * helpers (Fmt, Catalog) at it. Call from Activity.attachBaseContext and from background workers.
     */
    fun wrap(base: Context): Context {
        val code = get(base)
        val locale = if (code == SYSTEM) systemLocale() else Locale(code)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        val wrapped = base.createConfigurationContext(config)

        Fmt.attach(wrapped)
        Catalog.attach(wrapped.resources, languages.map { resourcesFor(base, it) })
        return wrapped
    }

    /** Resources for one specific language, regardless of the current setting. */
    fun resourcesFor(context: Context, code: String): Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(code))
        return context.createConfigurationContext(config).resources
    }

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]
}

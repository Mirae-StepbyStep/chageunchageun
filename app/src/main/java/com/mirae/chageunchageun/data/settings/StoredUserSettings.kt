package com.mirae.chageunchageun.data.settings

import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import com.mirae.chageunchageun.domain.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** String key-value storage under the settings. Lets the settings logic run in JVM tests. */
interface SettingsStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun clear()
}

/** For tests and previews. Share one instance between two [StoredUserSettings] to simulate a restart. */
class InMemorySettingsStore : SettingsStore {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun clear() = values.clear()
}

/** App settings kept in a [SettingsStore]. A missing or unknown stored value reads as the default. */
class StoredUserSettings(private val store: SettingsStore) : UserSettings {

    private val level = MutableStateFlow(enumOr(store.read(KEY_LEVEL), AdvisorLevel.DETAILED))
    private val consent = MutableStateFlow(enumOr(store.read(KEY_CONSENT), LocationConsent.NOT_ASKED))
    private val completed = MutableStateFlow(store.read(KEY_COMPLETED) == "true")

    override val advisorLevel: StateFlow<AdvisorLevel> = level
    override val locationConsent: StateFlow<LocationConsent> = consent
    override val firstRunCompleted: StateFlow<Boolean> = completed

    override fun setAdvisorLevel(level: AdvisorLevel) {
        store.write(KEY_LEVEL, level.name)
        this.level.value = level
    }

    override fun setLocationConsent(consent: LocationConsent) {
        store.write(KEY_CONSENT, consent.name)
        this.consent.value = consent
    }

    override fun completeFirstRun() {
        store.write(KEY_COMPLETED, "true")
        completed.value = true
    }

    override fun resetFirstRun() {
        store.clear()
        level.value = AdvisorLevel.DETAILED
        consent.value = LocationConsent.NOT_ASKED
        completed.value = false
    }

    private companion object {
        const val KEY_LEVEL = "advisor_level"
        const val KEY_CONSENT = "location_consent"
        const val KEY_COMPLETED = "first_run_completed"
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

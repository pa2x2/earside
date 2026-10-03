package eu.darken.capod.rules.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRulesSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "device_rules")

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val rules = dataStore.createValue("rules.data", DeviceRulesStorage(), json, onErrorFallbackToDefault = true)

    val runStates = dataStore.createValue("rules.runstates", RuleRunStates(), json, onErrorFallbackToDefault = true)

    /** Devices whose owner wants a notification each time one of their rules applies. */
    val notifyProfiles = dataStore.createValue("rules.notify", NotifyProfiles(), json, onErrorFallbackToDefault = true)
}

/**
 * Rules are stored as raw JSON per profile and decoded one by one in [DeviceRulesRepo], so a rule
 * with a trigger or action this version doesn't know can't break the others or get lost on save.
 */
@Serializable
data class DeviceRulesStorage(
    @SerialName("profiles") val profiles: Map<ProfileId, List<JsonObject>> = emptyMap(),
)

@Serializable
data class RuleRunStates(
    @SerialName("states") val states: Map<RuleId, RuleRunState> = emptyMap(),
)

@Serializable
data class NotifyProfiles(
    @SerialName("profiles") val profiles: Set<ProfileId> = emptySet(),
)

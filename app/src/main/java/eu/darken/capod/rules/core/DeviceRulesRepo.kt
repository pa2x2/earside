package eu.darken.capod.rules.core

import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RuleEntry {
    val id: RuleId

    data class Known(val rule: DeviceRule) : RuleEntry {
        override val id: RuleId get() = rule.id
    }

    /** Saved by a newer Earside with a trigger or action this version doesn't know. Kept as stored. */
    data class Unsupported(override val id: RuleId, val raw: JsonObject) : RuleEntry
}

/** A removed rule and where it was, so it can be put back by Undo. */
data class RemovedRule(val profileId: ProfileId, val index: Int, val raw: JsonObject)

/**
 * Rules per device profile, in list order. Writes only re-encode the rule being changed; every
 * other stored rule is kept byte for byte, including ones this version can't read.
 */
@Singleton
class DeviceRulesRepo @Inject constructor(
    private val settings: DeviceRulesSettings,
    @SerializationCapod private val json: Json,
) {

    private val mutex = Mutex()

    val rules: Flow<Map<ProfileId, List<RuleEntry>>> = settings.rules.flow
        .map { storage -> storage.profiles.mapValues { (_, raws) -> raws.map { decode(it) } } }
        .distinctUntilChanged()

    fun rulesFor(profileId: ProfileId): Flow<List<RuleEntry>> = rules
        .map { it[profileId].orEmpty() }
        .distinctUntilChanged()

    suspend fun addRule(profileId: ProfileId, rule: DeviceRule) = edit(profileId) { raws ->
        log(TAG, VERBOSE) { "addRule($profileId, $rule)" }
        raws + encode(rule)
    }

    suspend fun updateRule(profileId: ProfileId, rule: DeviceRule) = edit(profileId) { raws ->
        val index = raws.indexOfFirst { it.ruleId == rule.id }
        if (index == -1) {
            log(TAG, WARN) { "updateRule($profileId): ${rule.id} not found" }
            return@edit raws
        }
        log(TAG, VERBOSE) { "updateRule($profileId, $rule)" }
        raws.toMutableList().apply { set(index, encode(rule)) }
    }

    suspend fun removeRule(profileId: ProfileId, ruleId: RuleId): RemovedRule? {
        var removed: RemovedRule? = null
        edit(profileId) { raws ->
            val index = raws.indexOfFirst { it.ruleId == ruleId }
            if (index == -1) return@edit raws
            removed = RemovedRule(profileId, index, raws[index])
            raws.toMutableList().apply { removeAt(index) }
        }
        log(TAG, VERBOSE) { "removeRule($profileId, $ruleId) -> ${removed != null}" }
        return removed
    }

    suspend fun restoreRule(removed: RemovedRule) = edit(removed.profileId) { raws ->
        if (raws.any { it.ruleId == removed.raw.ruleId }) return@edit raws
        raws.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.raw) }
    }

    suspend fun deleteAll(profileId: ProfileId) = mutex.withLock {
        settings.rules.value(settings.rules.value().let { it.copy(profiles = it.profiles - profileId) })
        log(TAG, VERBOSE) { "deleteAll($profileId)" }
    }

    suspend fun clear() = mutex.withLock {
        settings.rules.value(DeviceRulesStorage())
    }

    private suspend fun edit(profileId: ProfileId, transform: (List<JsonObject>) -> List<JsonObject>) = mutex.withLock {
        val storage = settings.rules.value()
        val updated = transform(storage.profiles[profileId].orEmpty())
        val profiles = if (updated.isEmpty()) storage.profiles - profileId else storage.profiles + (profileId to updated)
        settings.rules.value(storage.copy(profiles = profiles))
    }

    private fun encode(rule: DeviceRule): JsonObject = json.encodeToJsonElement(DeviceRule.serializer(), rule).jsonObject

    private fun decode(raw: JsonObject): RuleEntry = try {
        RuleEntry.Known(json.decodeFromJsonElement(DeviceRule.serializer(), raw))
    } catch (e: SerializationException) {
        log(TAG, WARN) { "Keeping unreadable rule as unsupported: ${e.message}" }
        RuleEntry.Unsupported(raw.ruleId ?: raw.hashCode().toString(), raw)
    } catch (e: IllegalArgumentException) {
        log(TAG, WARN) { "Keeping unreadable rule as unsupported: ${e.message}" }
        RuleEntry.Unsupported(raw.ruleId ?: raw.hashCode().toString(), raw)
    }

    private val JsonObject.ruleId: RuleId? get() = this["id"]?.jsonPrimitive?.contentOrNull

    companion object {
        private val TAG = logTag("Rules", "Repo")
    }
}

package eu.darken.capod.common.updater

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_updates")

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val checkOnLaunch = dataStore.createValue("updates.check.launch", true)

    val channel = dataStore.createValue("updates.channel", UpdateChannel.STABLE, json, onErrorFallbackToDefault = true)

    /** The version "Skip this version" was used on. The launch check stays quiet for it and older ones. */
    val skippedVersion = dataStore.createValue<String?>(
        key = stringPreferencesKey("updates.skipped.version"),
        reader = { raw -> raw as? String },
        writer = { value -> value },
    )
}

@Serializable
enum class UpdateChannel(@StringRes val labelRes: Int) {
    @SerialName("stable") STABLE(R.string.settings_updates_channel_stable),
    @SerialName("prerelease") PRERELEASE(R.string.settings_updates_channel_prerelease),
}

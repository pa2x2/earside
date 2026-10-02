package eu.darken.capod.common.updater

import android.content.Context
import androidx.annotation.StringRes

/** A failed update check, download or install, with a message for the user. */
class UpdateException(
    @StringRes private val messageRes: Int,
    private val formatArgs: List<Any> = emptyList(),
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: cause?.message, cause) {

    fun describe(context: Context): String = context.getString(messageRes, *formatArgs.toTypedArray())
}

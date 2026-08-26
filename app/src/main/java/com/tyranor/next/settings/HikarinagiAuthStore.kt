package com.tyranor.next.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class HikarinagiAuthStatus(
    val authorized: Boolean,
    val needsReauth: Boolean,
    val expiresAtMillis: Long,
    val lastError: String,
)

object HikarinagiAuthStore {
    private const val PREFS = "hikarinagi_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_LAST_ERROR = "last_error"

    val statusVersion: MutableState<Int> = mutableStateOf(0)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getStatus(context: Context): HikarinagiAuthStatus {
        val p = prefs(context)
        val accessToken = p.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        val refreshToken = p.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        val lastError = p.getString(KEY_LAST_ERROR, "").orEmpty()
        return HikarinagiAuthStatus(
            authorized = accessToken.isNotBlank() || refreshToken.isNotBlank(),
            needsReauth = lastError.isNotBlank(),
            expiresAtMillis = p.getLong(KEY_EXPIRES_AT, 0L),
            lastError = lastError,
        )
    }

    fun getAccessToken(context: Context): String =
        prefs(context).getString(KEY_ACCESS_TOKEN, "").orEmpty()

    fun getRefreshToken(context: Context): String =
        prefs(context).getString(KEY_REFRESH_TOKEN, "").orEmpty()

    fun getExpiresAtMillis(context: Context): Long =
        prefs(context).getLong(KEY_EXPIRES_AT, 0L)

    fun saveTokens(context: Context, accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .putString(KEY_LAST_ERROR, "")
            .apply()
        bump()
    }

    fun markNeedsReauth(context: Context, reason: String) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, "")
            .putString(KEY_REFRESH_TOKEN, "")
            .putLong(KEY_EXPIRES_AT, 0L)
            .putString(KEY_LAST_ERROR, reason.trim())
            .apply()
        bump()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        bump()
    }

    private fun bump() {
        statusVersion.value += 1
    }
}

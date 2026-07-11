package com.invictus.link

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The bridge session token is stored here (AES-256), not in plain SharedPreferences.
 */
object LinkSecureStore {
    private const val FILE = "invictus_link_secure"
    private const val KEY_SESSION_TOKEN = "session_token"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun loadSessionToken(context: Context): String? =
        prefs(context).getString(KEY_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveSessionToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_SESSION_TOKEN, token).apply()
    }

    fun clearSessionToken(context: Context) {
        prefs(context).edit().remove(KEY_SESSION_TOKEN).apply()
    }
}

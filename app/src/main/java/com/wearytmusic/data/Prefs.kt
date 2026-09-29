package com.wearytmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the YouTube Music session cookie. Stored encrypted; if the keystore is unusable the
 * cookie is kept in memory only (never written in plain text).
 */
object Prefs {
    private const val KEY_COOKIE = "cookie"
    private var sp: SharedPreferences? = null

    private val _cookie = MutableStateFlow<String?>(null)
    val cookie: StateFlow<String?> = _cookie.asStateFlow()

    fun init(context: Context) {
        sp = runCatching {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "secure_prefs", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
        _cookie.value = sp?.getString(KEY_COOKIE, null)
    }

    fun setCookie(value: String?) {
        _cookie.value = value
        sp?.edit()?.apply { if (value == null) remove(KEY_COOKIE) else putString(KEY_COOKIE, value) }?.apply()
    }
}

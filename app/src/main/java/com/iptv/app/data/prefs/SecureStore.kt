package com.iptv.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getHost(): String = prefs.getString(KEY_HOST, "").orEmpty()
    fun getUser(): String = prefs.getString(KEY_USER, "").orEmpty()
    fun getPass(): String = prefs.getString(KEY_PASS, "").orEmpty()
    fun getPin(): String? = prefs.getString(KEY_PIN, null)
    fun isPinSet(): Boolean = prefs.contains(KEY_PIN)
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED, false)

    fun saveCredentials(host: String, user: String, pass: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_USER, user)
            .putString(KEY_PASS, pass)
            .putBoolean(KEY_LOGGED, true)
            .apply()
    }

    fun setLoggedOut() {
        prefs.edit().putBoolean(KEY_LOGGED, false).apply()
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "tartatv_secure"
        private const val KEY_HOST = "host"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_PIN = "pin"
        private const val KEY_LOGGED = "logged"
    }
}

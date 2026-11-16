package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.util.UniTrackLogger

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            UniTrackLogger.w("CredentialStore: EncryptedSharedPreferences initialization failed: ${e.message}")
            null
        }
    }

    fun save(studentId: String, password: String) {
        val currentPrefs = prefs ?: return
        if (studentId.isBlank() || password.isBlank()) return
        currentPrefs.edit()
            .putString(KEY_STUDENT_ID, studentId.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun load(): Pair<String, String>? {
        val currentPrefs = prefs ?: return null
        val id = currentPrefs.getString(KEY_STUDENT_ID, null)
        val pass = currentPrefs.getString(KEY_PASSWORD, null)
        return if (!id.isNullOrBlank() && !pass.isNullOrBlank()) {
            Pair(id, pass)
        } else null
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    fun hasCredentials(): Boolean {
        return load() != null
    }

    companion object {
        private const val PREFS_FILENAME = "unitrack_secure_creds"
        private const val KEY_STUDENT_ID = "encrypted_student_id"
        private const val KEY_PASSWORD = "encrypted_password"
    }
}

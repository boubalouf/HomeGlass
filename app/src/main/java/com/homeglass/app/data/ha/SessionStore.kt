package com.homeglass.app.data.ha

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.homeglass.app.domain.ha.HaSession
import kotlinx.serialization.json.Json

/** Persistance de la session (instance + jetons). */
interface SessionStore {
    fun read(): HaSession?
    fun save(session: HaSession)
    fun clear()
}

/**
 * Stockage chiffré de la session.
 *
 * Les jetons ne sortent jamais de `EncryptedSharedPreferences` (clé AES-256 gérée par
 * Android Keystore) : aucune sauvegarde possible (`allowBackup="false"`), aucune valeur en
 * clair sur le disque.
 */
class EncryptedSessionStore(context: Context) : SessionStore {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): HaSession? {
        val raw = runCatching { prefs.getString(KEY_SESSION, null) }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(HaSession.serializer(), raw) }.getOrNull()
    }

    override fun save(session: HaSession) {
        val raw = json.encodeToString(HaSession.serializer(), session)
        runCatching { prefs.edit().putString(KEY_SESSION, raw).apply() }
    }

    override fun clear() {
        runCatching { prefs.edit().remove(KEY_SESSION).apply() }
    }

    private companion object {
        const val FILE_NAME = "homeglass_session"
        const val KEY_SESSION = "session"
    }
}

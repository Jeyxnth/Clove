package app.rasoi.sos

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Setup choices in EncryptedSharedPreferences (Keystore-backed AES-256).
 * If the Keystore is unavailable we fall back to memory only: nothing is ever written in the clear.
 */
class SosPrefs(context: Context) : ChoiceStore {
    private val sp: SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context, "x_store",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()
    private var memory = Choices()

    override fun load(): Choices = sp?.let {
        Choices(
            setupDone = it.getBoolean("d", false),
            locationConsent = it.getBoolean("l", false),
            contactEnabled = it.getBoolean("c", false),
            contactName = it.getString("cn", "") ?: "",
            contactPhone = it.getString("cp", "") ?: "",
            contactText = it.getString("ct", null) ?: Choices().contactText,
            contactIncludeLocation = it.getBoolean("cl", false),
        )
    } ?: memory

    override fun save(c: Choices) {
        memory = c
        sp?.edit()
            ?.putBoolean("d", c.setupDone)?.putBoolean("l", c.locationConsent)
            ?.putBoolean("c", c.contactEnabled)?.putString("cn", c.contactName)
            ?.putString("cp", c.contactPhone)?.putString("ct", c.contactText)
            ?.putBoolean("cl", c.contactIncludeLocation)?.apply()
    }

    override fun erase() {
        memory = Choices()
        sp?.edit()?.clear()?.apply()
    }
}

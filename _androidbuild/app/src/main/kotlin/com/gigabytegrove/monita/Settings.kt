package com.gigabytegrove.monita

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.gigabytegrove.monita.client.model.User
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.tinylog.kotlin.Logger

internal class Settings(context: Context) {
    companion object {
        private const val PREFS_NAME = "monita"
        private const val MIRROR_FILE = "monita-connection-state.json"
        private const val KEY_URL = "url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ADMIN = "admin"
        private const val KEY_VERSION = "version"
        private const val KEY_LEGACY_CERT = "cert"
        private const val KEY_CA_CERT_PATH = "caCertPath"
        private const val KEY_VALIDATE_SSL = "validateSSL"
        private const val KEY_CLIENT_CERT_PATH = "clientCertPath"
        private const val KEY_CLIENT_CERT_PASSWORD = "clientCertPass"
        private const val KEY_OIDC_CODE_VERIFIER = "oidc_code_verifier"
        private const val KEY_OIDC_STATE = "oidc_state"
        private const val LEGACY_KEY_MONITA_USER_ID = "muUserId"
        private const val KEY_INSTALLATION_ID = "installationId"
        private const val KEY_APP_LOCK_ENABLED = "appLockEnabled"
        private const val KEY_DEVICE_AUTH_MODE = "deviceAuthMode"
        private const val KEY_PROTECTED_CHANNEL_IDS_PREFIX = "protectedChannelIds"
        private const val KEY_CHANNEL_SOUND_PREFIX = "channelSound"
        const val CHANNEL_SOUND_SILENT = "__silent__"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mirrorFile = AtomicFile(File(context.noBackupFilesDir, MIRROR_FILE))

    val filesDir: String = context.filesDir.absolutePath

    var url: String
        get() = sharedPreferences.getString(KEY_URL, "")!!
        set(value) = mutate { putString(KEY_URL, value) }

    var token: String?
        get() = sharedPreferences.getString(KEY_TOKEN, null)
        set(value) = mutate {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }

    var user: User? = null
        get() {
            val username = sharedPreferences.getString(KEY_USERNAME, null)
            val admin = sharedPreferences.getBoolean(KEY_ADMIN, false)
            return username?.let { User().name(it).admin(admin) }
        }
        private set

    var serverVersion: String
        get() = sharedPreferences.getString(KEY_VERSION, "UNKNOWN")!!
        set(value) = mutate { putString(KEY_VERSION, value) }

    var legacyCert: String?
        get() = sharedPreferences.getString(KEY_LEGACY_CERT, null)
        set(value) = mutate {
            if (value == null) remove(KEY_LEGACY_CERT) else putString(KEY_LEGACY_CERT, value)
        }

    var caCertPath: String?
        get() = sharedPreferences.getString(KEY_CA_CERT_PATH, null)
        set(value) = mutate {
            if (value == null) remove(KEY_CA_CERT_PATH) else putString(KEY_CA_CERT_PATH, value)
        }

    var validateSSL: Boolean
        get() = sharedPreferences.getBoolean(KEY_VALIDATE_SSL, true)
        set(value) = mutate { putBoolean(KEY_VALIDATE_SSL, value) }

    var clientCertPath: String?
        get() = sharedPreferences.getString(KEY_CLIENT_CERT_PATH, null)
        set(value) = mutate {
            if (value == null) remove(KEY_CLIENT_CERT_PATH) else putString(KEY_CLIENT_CERT_PATH, value)
        }

    var clientCertPassword: String?
        get() = sharedPreferences.getString(KEY_CLIENT_CERT_PASSWORD, null)
        set(value) = mutate {
            if (value == null) remove(KEY_CLIENT_CERT_PASSWORD)
            else putString(KEY_CLIENT_CERT_PASSWORD, value)
        }

    var oidcCodeVerifier: String?
        get() = sharedPreferences.getString(KEY_OIDC_CODE_VERIFIER, null)
        set(value) = mutate {
            if (value == null) remove(KEY_OIDC_CODE_VERIFIER)
            else putString(KEY_OIDC_CODE_VERIFIER, value)
        }

    var oidcState: String?
        get() = sharedPreferences.getString(KEY_OIDC_STATE, null)
        set(value) = mutate {
            if (value == null) remove(KEY_OIDC_STATE) else putString(KEY_OIDC_STATE, value)
        }

    val monitaUserId: Long?
        get() =
            if (sharedPreferences.contains(LEGACY_KEY_MONITA_USER_ID)) {
                sharedPreferences.getLong(LEGACY_KEY_MONITA_USER_ID, -1L).takeIf { it >= 0L }
            } else {
                null
            }

    val installationId: String
        get() = sharedPreferences.getString(KEY_INSTALLATION_ID, null)
            ?: error("Installation identity was not initialized")

    var appLockEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = mutate { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    var deviceAuthMode: String
        get() = sharedPreferences.getString(KEY_DEVICE_AUTH_MODE, "either") ?: "either"
        set(value) = mutate { putString(KEY_DEVICE_AUTH_MODE, value) }

    init {
        restoreMirrorIfNeeded()
        ensureInstallationId()
        persistMirror()
    }

    fun tokenExists(): Boolean = !token.isNullOrEmpty()

    fun clearAuthentication() {
        mutate {
            remove(KEY_TOKEN)
            remove(KEY_OIDC_CODE_VERIFIER)
            remove(KEY_OIDC_STATE)
            remove(KEY_USERNAME)
            remove(KEY_ADMIN)
            remove(KEY_VERSION)
            remove(LEGACY_KEY_MONITA_USER_ID)
        }
    }

    fun clear() {
        mutate {
            putString(KEY_URL, "")
            remove(KEY_TOKEN)
            remove(KEY_OIDC_CODE_VERIFIER)
            remove(KEY_OIDC_STATE)
            remove(KEY_USERNAME)
            remove(KEY_ADMIN)
            remove(KEY_VERSION)
            remove(LEGACY_KEY_MONITA_USER_ID)
            putBoolean(KEY_VALIDATE_SSL, true)
            remove(KEY_LEGACY_CERT)
            remove(KEY_CA_CERT_PATH)
            remove(KEY_CLIENT_CERT_PATH)
            remove(KEY_CLIENT_CERT_PASSWORD)
        }
    }

    fun setUser(name: String?, admin: Boolean) {
        mutate {
            if (name == null) remove(KEY_USERNAME) else putString(KEY_USERNAME, name)
            putBoolean(KEY_ADMIN, admin)
        }
    }

    fun setMonitaIdentity(id: Long, name: String? = null) {
        mutate {
            putLong(LEGACY_KEY_MONITA_USER_ID, id)
            if (!name.isNullOrBlank()) putString(KEY_USERNAME, name)
        }
    }

    fun sslSettings(): SSLSettings =
        SSLSettings(
            validateSSL,
            caCertPath,
            clientCertPath,
            clientCertPassword
        )

    fun protectedChannelIds(): Set<Long> =
        sharedPreferences.getStringSet(protectedChannelKey(), emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun isChannelProtected(appId: Long): Boolean = appId in protectedChannelIds()

    fun setChannelProtected(appId: Long, protected: Boolean) {
        val ids = protectedChannelIds().toMutableSet()
        if (protected) ids.add(appId) else ids.remove(appId)
        val committed =
            sharedPreferences.edit()
                .putStringSet(protectedChannelKey(), ids.map(Long::toString).toSet())
                .commit()
        if (!committed) {
            error("Could not persist protected Channel settings")
        }
    }

    fun channelSound(appId: Long): String? =
        sharedPreferences.getString(channelSoundKey(appId), null)

    fun setChannelSound(appId: Long, sound: String?) {
        val editor = sharedPreferences.edit()
        if (sound == null) {
            editor.remove(channelSoundKey(appId))
        } else {
            editor.putString(channelSoundKey(appId), sound)
        }
        if (!editor.commit()) {
            error("Could not persist Monita Channel notification sound")
        }
    }

    private fun channelSoundKey(appId: Long): String {
        val server = url.ifBlank { "unset-server" }
        val username = user?.name ?: "unset-user"
        return "$KEY_CHANNEL_SOUND_PREFIX::$server::$username::$appId"
    }

    private fun protectedChannelKey(): String {
        val server = url.ifBlank { "unset-server" }
        val username = user?.name ?: "unset-user"
        return "$KEY_PROTECTED_CHANNEL_IDS_PREFIX::$server::$username"
    }

    private fun ensureInstallationId() {
        if (!sharedPreferences.getString(KEY_INSTALLATION_ID, null).isNullOrBlank()) return

        val committed =
            sharedPreferences.edit()
                .putString(KEY_INSTALLATION_ID, UUID.randomUUID().toString())
                .commit()
        if (!committed) {
            error("Could not persist Monita Android installation identity")
        }
    }

    private fun mutate(block: SharedPreferences.Editor.() -> Unit) {
        val editor = sharedPreferences.edit()
        editor.block()
        if (!editor.commit()) {
            error("Could not persist Monita Android settings")
        }
        persistMirror()
    }

    private fun restoreMirrorIfNeeded() {
        val hasInstallationIdentity =
            !sharedPreferences.getString(KEY_INSTALLATION_ID, null).isNullOrBlank()
        if (hasInstallationIdentity || !mirrorFile.baseFile.exists()) return

        try {
            val json =
                mirrorFile.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                    JSONObject(reader.readText())
                }
            val editor = sharedPreferences.edit()

            restoreString(json, editor, KEY_URL)
            restoreString(json, editor, KEY_TOKEN)
            restoreString(json, editor, KEY_USERNAME)
            restoreString(json, editor, KEY_VERSION)
            restoreString(json, editor, KEY_LEGACY_CERT)
            restoreString(json, editor, KEY_CA_CERT_PATH)
            restoreString(json, editor, KEY_CLIENT_CERT_PATH)
            restoreString(json, editor, KEY_CLIENT_CERT_PASSWORD)
            restoreString(json, editor, KEY_INSTALLATION_ID)

            if (json.has(KEY_ADMIN)) editor.putBoolean(KEY_ADMIN, json.optBoolean(KEY_ADMIN, false))
            if (json.has(KEY_VALIDATE_SSL)) {
                editor.putBoolean(KEY_VALIDATE_SSL, json.optBoolean(KEY_VALIDATE_SSL, true))
            }
            if (json.has(LEGACY_KEY_MONITA_USER_ID) && !json.isNull(LEGACY_KEY_MONITA_USER_ID)) {
                editor.putLong(LEGACY_KEY_MONITA_USER_ID, json.optLong(LEGACY_KEY_MONITA_USER_ID, -1L))
            }

            if (!editor.commit()) {
                error("Could not restore Monita Android connection state")
            }
            Logger.info("Restored Monita Android connection state from durable mirror")
        } catch (e: Exception) {
            Logger.warn(e, "Could not restore Monita Android connection-state mirror")
        }
    }

    private fun restoreString(
        json: JSONObject,
        editor: SharedPreferences.Editor,
        key: String
    ) {
        if (!json.has(key)) return
        if (json.isNull(key)) {
            editor.remove(key)
        } else {
            editor.putString(key, json.optString(key, ""))
        }
    }

    private fun persistMirror() {
        val json =
            JSONObject().apply {
                putNullable(KEY_URL, sharedPreferences.getString(KEY_URL, ""))
                putNullable(KEY_TOKEN, sharedPreferences.getString(KEY_TOKEN, null))
                putNullable(KEY_USERNAME, sharedPreferences.getString(KEY_USERNAME, null))
                put(KEY_ADMIN, sharedPreferences.getBoolean(KEY_ADMIN, false))
                putNullable(KEY_VERSION, sharedPreferences.getString(KEY_VERSION, null))
                putNullable(KEY_LEGACY_CERT, sharedPreferences.getString(KEY_LEGACY_CERT, null))
                putNullable(KEY_CA_CERT_PATH, sharedPreferences.getString(KEY_CA_CERT_PATH, null))
                put(KEY_VALIDATE_SSL, sharedPreferences.getBoolean(KEY_VALIDATE_SSL, true))
                putNullable(
                    KEY_CLIENT_CERT_PATH,
                    sharedPreferences.getString(KEY_CLIENT_CERT_PATH, null)
                )
                putNullable(
                    KEY_CLIENT_CERT_PASSWORD,
                    sharedPreferences.getString(KEY_CLIENT_CERT_PASSWORD, null)
                )
                if (sharedPreferences.contains(LEGACY_KEY_MONITA_USER_ID)) {
                    put(LEGACY_KEY_MONITA_USER_ID, sharedPreferences.getLong(LEGACY_KEY_MONITA_USER_ID, -1L))
                } else {
                    put(LEGACY_KEY_MONITA_USER_ID, JSONObject.NULL)
                }
                putNullable(
                    KEY_INSTALLATION_ID,
                    sharedPreferences.getString(KEY_INSTALLATION_ID, null)
                )
            }

        var output: java.io.FileOutputStream? = null
        try {
            output = mirrorFile.startWrite()
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
            mirrorFile.finishWrite(output)
        } catch (e: Exception) {
            output?.let { mirrorFile.failWrite(it) }
            Logger.warn(e, "Could not persist Monita Android connection-state mirror")
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }
}

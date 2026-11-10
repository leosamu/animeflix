package com.animeflix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.net.URLEncoder

data class ViewingProfile(val id: String, val name: String)
data class PasswordRecovery(val email: String)
data class AccountState(
    val accountId: String? = null, val email: String = "", val profiles: List<ViewingProfile> = emptyList(),
    val selected: ViewingProfile? = null, val preview: Boolean = false
)
private data class Session(val accountId: String, val email: String, val access: String, val refresh: String, val expiresAt: Long) {
    fun json() = JSONObject().put("accountId", accountId).put("email", email).put("access", access).put("refresh", refresh).put("expiresAt", expiresAt)
    companion object {
        fun fromAuth(json: JSONObject) = Session(json.getJSONObject("user").getString("id"), json.getJSONObject("user").optString("email"),
            json.getString("access_token"), json.getString("refresh_token"), System.currentTimeMillis() + json.getLong("expires_in") * 1000)
        fun fromDisk(json: JSONObject) = Session(json.getString("accountId"), json.getString("email"), json.getString("access"), json.getString("refresh"), json.getLong("expiresAt"))
    }
}

class AccountManager private constructor(context: Context) {
    val callbackUrl = "${context.packageName}://auth-callback"
    val config = SupabaseConfig.bundled()
    private val api: CloudTransport = SupabaseApi(config)
    private val store = SecureAccountStore(context)
    private val mutex = Mutex()
    private var session: Session? = null
    private var recoveryAccess: String? = null
    private val mutableRecovery = MutableStateFlow<PasswordRecovery?>(null)
    val recovery = mutableRecovery.asStateFlow()
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    init {
        store.read()?.let { disk -> runCatching {
            session = disk.optJSONObject("session")?.let(Session::fromDisk)
            val profiles = disk.optJSONArray("profiles")?.objects()?.map { ViewingProfile(it.getString("id"), it.getString("name")) }.orEmpty()
            val preview = disk.optBoolean("preview")
            mutable.value = AccountState(if (preview) "local-preview" else session?.accountId, session?.email.orEmpty(), profiles,
                profiles.find { it.id == disk.optString("selected") }, preview)
        } }
    }
    private fun persist() {
        val current = mutable.value
        store.write(JSONObject().put("session", session?.json()).put("profiles", JSONArray(current.profiles.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("selected", current.selected?.id).put("preview", current.preview))
    }
    suspend fun signIn(email: String, password: String) = mutex.withLock {
        val response = api.request("/auth/v1/token?grant_type=password", "POST", body = JSONObject().put("email", email.trim()).put("password", password).toString())
        val signed = Session.fromAuth(JSONObject(response))
        session = signed
        mutable.value = AccountState(signed.accountId, signed.email)
        persist()
    }
    /** Returns true when email confirmation is required. Password is never persisted. */
    suspend fun signUp(email: String, password: String): Boolean = mutex.withLock {
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        val json = JSONObject(api.request("/auth/v1/signup?redirect_to=${URLEncoder.encode(callbackUrl, "UTF-8")}", "POST", body = JSONObject().put("email", email.trim()).put("password", password).toString()))
        if (json.optString("access_token").isBlank()) true else {
            val signed = Session.fromAuth(json)
            session = signed; mutable.value = AccountState(signed.accountId, signed.email); persist(); false
        }
    }
    suspend fun requestPasswordReset(email: String) = mutex.withLock {
        require(email.trim().contains('@')) { "Introduce tu correo electrónico." }
        api.request("/auth/v1/recover?redirect_to=${URLEncoder.encode(callbackUrl, "UTF-8")}", "POST", body = JSONObject().put("email", email.trim()).toString())
        Unit
    }
    /** A recovery link never signs into an account automatically. User must choose a new password. */
    suspend fun openAuthCallback(raw: String, allowLegacyLocalhost: Boolean = false): Boolean = mutex.withLock {
        val callback = AuthCallback.parse(raw, callbackUrl, allowLegacyLocalhost, config.url)
        if (callback.type == "signup") return@withLock false
        recoveryAccess = null; mutableRecovery.value = null
        val token = callback.verificationHash?.let { hash ->
            JSONObject(api.request("/auth/v1/verify", "POST", body = JSONObject().put("token_hash", hash).put("type", "recovery").toString())).getString("access_token")
        } ?: checkNotNull(callback.accessToken)
        val user = JSONObject(api.request("/auth/v1/user", token = token))
        val email = user.getString("email")
        require(email.isNotBlank()) { "No se pudo verificar la cuenta del enlace." }
        recoveryAccess = token
        mutableRecovery.value = PasswordRecovery(email)
        true
    }
    suspend fun cancelPasswordReset() = mutex.withLock { recoveryAccess = null; mutableRecovery.value = null }
    suspend fun updateRecoveryPassword(password: String, repeated: String) = mutex.withLock {
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        require(password == repeated) { "Las contraseñas no coinciden." }
        val token = recoveryAccess ?: error("Abre un enlace de recuperación válido.")
        api.request("/auth/v1/user", "PUT", token, JSONObject().put("password", password).toString())
        // Revoke this recovery session's refresh token, including links copied outside the app.
        try { api.request("/auth/v1/logout?scope=local", "POST", token) }
        catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException) throw failure }
        recoveryAccess = null; mutableRecovery.value = null
        session = null; store.clear(); mutable.value = AccountState()
    }
    suspend fun beginPreview() = mutex.withLock {
        session = null
        mutable.value = AccountState("local-preview", profiles = listOf(ViewingProfile(UUID.randomUUID().toString(), "Niño"), ViewingProfile(UUID.randomUUID().toString(), "Niña")), preview = true)
        persist()
    }
    suspend fun select(profile: ViewingProfile?) = mutex.withLock {
        require(profile == null || profile in mutable.value.profiles)
        mutable.value = mutable.value.copy(selected = profile)
        persist()
    }
    suspend fun signOut() = mutex.withLock {
        // Local logout always succeeds, including offline. Cached data stays isolated by account/profile.
        session = null; store.clear(); mutable.value = AccountState()
    }
    suspend fun refreshProfiles() {
        val account = mutable.value
        if (account.preview || account.accountId == null) return
        val result = authorized(account.accountId) { token -> api.request("/rest/v1/viewing_profiles?select=id,name&order=created_at.asc", token = token) }
        mutex.withLock {
            if (mutable.value.accountId != account.accountId) return@withLock
            val profiles = JSONArray(result).objects().map { ViewingProfile(it.getString("id"), it.getString("name")) }
            mutable.value = mutable.value.copy(profiles = profiles, selected = profiles.find { it.id == mutable.value.selected?.id })
            persist()
        }
    }
    suspend fun createProfile(name: String) {
        val trimmed = name.trim()
        require(trimmed.length in 1..32) { "El nombre debe tener entre 1 y 32 caracteres." }
        val account = mutable.value
        val accountId = requireNotNull(account.accountId)
        if (account.preview) {
            mutex.withLock { mutable.value = mutable.value.copy(profiles = mutable.value.profiles + ViewingProfile(UUID.randomUUID().toString(), trimmed)); persist() }
        } else {
            authorized(accountId) { token -> api.request("/rest/v1/viewing_profiles", "POST", token, JSONObject().put("name", trimmed).put("account_id", accountId).toString()) }
            refreshProfiles()
        }
    }
    internal suspend fun <T> authorized(accountId: String, action: suspend (String) -> T): T = mutex.withLock {
        check(mutable.value.accountId == accountId && !mutable.value.preview) { "La cuenta ha cambiado. Vuelve a iniciar sesión." }
        var signed = checkNotNull(session)
        suspend fun renew(): Session {
            try {
                val response = api.request("/auth/v1/token?grant_type=refresh_token", "POST", body = JSONObject().put("refresh_token", signed.refresh).toString())
                val fresh = Session.fromAuth(JSONObject(response))
                check(fresh.accountId == accountId)
                session = fresh; persist(); return fresh
            } catch (failure: CloudException) {
                if (failure.status in listOf(400, 401, 403)) { session = null; store.clear(); mutable.value = AccountState() }
                throw failure
            }
        }
        if (signed.expiresAt <= System.currentTimeMillis() + 60_000) signed = renew()
        try { action(signed.access) }
        catch (failure: CloudException) {
            if (failure.status != 401) throw failure
            signed = renew(); action(signed.access)
        }
    }
    internal suspend fun push(accountId: String, profileId: String, entry: ProfileEntry) = authorized(accountId) { token ->
        val body = JSONObject().put("account_id", accountId).put("profile_id", profileId).put("kind", entry.kind)
            .put("item_key", entry.itemKey).put("value", JSONObject(entry.value))
        api.request("/rest/v1/profile_entries?on_conflict=profile_id,kind,item_key", "POST", token, body.toString(), "resolution=merge-duplicates,return=minimal")
        Unit
    }
    internal suspend fun pull(accountId: String, profileId: String): List<ProfileEntry> {
        val all = mutableListOf<ProfileEntry>()
        var offset = 0
        while (true) {
            val text = authorized(accountId) { token -> api.request("/rest/v1/profile_entries?profile_id=eq.$profileId&select=kind,item_key,value&order=kind.asc,item_key.asc&limit=500&offset=$offset", token = token) }
            val page = JSONArray(text).objects().map { ProfileEntry(it.getString("kind"), it.getString("item_key"), it.getJSONObject("value").toString(), dirty = false) }
            all.addAll(page)
            if (page.size < 500) return all
            offset += page.size
        }
    }
    companion object {
        @Volatile private var instance: AccountManager? = null
        fun create(context: Context): AccountManager = instance ?: synchronized(this) { instance ?: AccountManager(context.applicationContext).also { instance = it } }
    }
}

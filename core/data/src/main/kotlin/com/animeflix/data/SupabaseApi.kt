package com.animeflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class SupabaseConfig(val url: String, val publicKey: String) {
    val configured: Boolean get() = url.isNotBlank() && publicKey.isNotBlank()
    fun validate() {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "La URL de Supabase debe usar HTTPS." }
        require(publicKey.startsWith("sb_publishable_") || publicKey.startsWith("eyJ")) { "Usa una clave pública de Supabase." }
        if (publicKey.startsWith("eyJ")) {
            val payload = String(android.util.Base64.decode(publicKey.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            require(JSONObject(payload).optString("role") == "anon") { "No se permite una clave secreta en la app." }
        }
    }
    companion object { fun bundled() = SupabaseConfig(BuildConfig.SUPABASE_URL.trimEnd('/'), BuildConfig.SUPABASE_PUBLISHABLE_KEY) }
}

class CloudException(val status: Int, override val message: String) : Exception(message)

interface CloudTransport {
    suspend fun request(path: String, method: String = "GET", token: String? = null, body: String? = null, prefer: String? = null): String
}

/** Only the public API key and the signed-in user's token are sent to the configured HTTPS origin. */
class SupabaseApi(private val config: SupabaseConfig) : CloudTransport {
    override suspend fun request(path: String, method: String, token: String?, body: String?, prefer: String?): String = withContext(Dispatchers.IO) {
        config.validate()
        require(path.startsWith("/auth/v1/") || path.startsWith("/rest/v1/"))
        val connection = URL(config.url + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = method
            connection.setRequestProperty("apikey", config.publicKey)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output -> output.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val code = runCatching {
                    val error = JSONObject(text)
                    error.optString("error_code").ifBlank { error.optString("code") }
                }.getOrDefault("")
                throw CloudException(status, when {
                    code == "email_not_confirmed" -> "Confirma tu correo antes de iniciar sesión."
                    code == "invalid_credentials" -> "El correo o la contraseña no son correctos."
                    code == "same_password" -> "Elige una contraseña diferente de la anterior."
                    code == "otp_expired" -> "El enlace ha caducado o ya se ha utilizado. Solicita otro correo."
                    code == "weak_password" -> "La contraseña no cumple los requisitos del proyecto. Prueba una más larga."
                    code == "over_email_send_rate_limit" -> "Se ha alcanzado el límite de correos. Usa el último enlace recibido o inténtalo más tarde."
                    status == 429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
                    status == 401 -> "La sesión ha caducado. Inicia sesión de nuevo."
                    status == 403 -> "Tu cuenta no tiene permiso para esta operación."
                    status == 400 && path.contains("signup") -> "No se pudo crear la cuenta. Revisa el correo y la contraseña."
                    else -> "No se pudo completar la operación en Supabase (HTTP $status)."
                })
            }
            text
        } finally { connection.disconnect() }
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

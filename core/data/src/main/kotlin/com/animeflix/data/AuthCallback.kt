package com.animeflix.data

import java.net.URI
import java.net.URLDecoder

/** Tokens are opaque here; the server validates the bearer token before any account is displayed. */
data class AuthCallback(val type: String, val accessToken: String?, val verificationHash: String? = null) {
    override fun toString() = "AuthCallback(type=$type, credentials=[redacted])"
    companion object {
        fun parse(raw: String, callbackUrl: String, allowLegacyLocalhost: Boolean = false, supabaseUrl: String? = null): AuthCallback {
            require(raw.length <= 24_000) { "El enlace no es válido." }
            var link = raw.trim()
            if (allowLegacyLocalhost) {
                // Clipboard copies from email/chat may include link markup rather than a plain URI.
                if (link.startsWith("[") && link.endsWith(")") && "](" in link) {
                    link = link.substring(link.lastIndexOf("](") + 2, link.length - 1).trim()
                }
                if ((link.startsWith("<") && link.endsWith(">")) || (link.startsWith("`") && link.endsWith("`"))) {
                    link = link.substring(1, link.length - 1).trim()
                }
                link = link.replace("\\&", "&").replace("&amp;", "&")
            }
            val uri = try { URI(link) } catch (_: Exception) { throw IllegalArgumentException("El enlace no es válido. Copia la dirección completa del enlace.") }
            val expected = URI(callbackUrl)
            val ownCallback = uri.scheme == expected.scheme && uri.rawAuthority == expected.rawAuthority && uri.path.orEmpty() in listOf("", "/")
            val legacy = allowLegacyLocalhost && uri.scheme == "http" && uri.host == "localhost" && uri.port == 3000 && uri.path.orEmpty() in listOf("", "/")
            val server = supabaseUrl?.let { URI(it) }
            val verification = allowLegacyLocalhost && server != null && uri.scheme == "https" && uri.rawAuthority == server.rawAuthority && uri.path == "/auth/v1/verify" && uri.fragment == null
            require(((ownCallback || legacy) && uri.query == null || verification) && uri.userInfo == null) { "El enlace no corresponde a AnimeFlix." }
            val values = mutableMapOf<String, String>()
            try {
                (if (verification) uri.rawQuery else uri.rawFragment).orEmpty().split('&').filter { it.isNotEmpty() }.forEach { field ->
                    val pair = field.split('=', limit = 2)
                    val key = URLDecoder.decode(pair[0], "UTF-8")
                    require(key !in values)
                    values[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
                }
            } catch (_: Exception) { throw IllegalArgumentException("El enlace no es válido.") }
            require(values["error"] == null && values["error_code"] == null) { "El enlace ha caducado o ya se ha utilizado. Solicita otro correo." }
            val type = values["type"]
            require(type in listOf("signup", "recovery")) { "Usa un enlace de confirmación o recuperación de AnimeFlix." }
            if (verification) {
                require(type == "recovery" && !(values.containsKey("token") && values.containsKey("token_hash"))) { "Usa el enlace de recuperación de contraseña." }
                val hash = values["token_hash"] ?: values["token"]
                require(hash != null && hash.matches(Regex("[A-Za-z0-9_-]{16,512}"))) { "El enlace de recuperación no es válido." }
                return AuthCallback(type, null, hash)
            }
            val token = values["access_token"]
            if (type == "recovery") {
                require(values["token_type"] == "bearer" && token != null && token.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))) { "El enlace de recuperación no es válido. Solicita uno nuevo." }
            }
            return AuthCallback(checkNotNull(type), token)
        }
    }
}

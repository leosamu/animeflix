package com.animeflix.data

import org.junit.Assert.*
import org.junit.Test

class AuthCallbackTest {
    private val callback = "com.animeflix.mobile.preview://auth-callback"
    private val fragment = "#type=recovery&token_type=bearer&access_token=fake.payload.signature"
    @Test fun acceptsOnlyThisBuildsCallback() {
        assertEquals("fake.payload.signature", AuthCallback.parse(callback + fragment, callback).accessToken)
        rejects("com.other.app://auth-callback$fragment")
        rejects("$callback/other$fragment")
        rejects("https://attacker.example/$fragment", legacy = true)
        rejects("http://localhost:3000.attacker.example/$fragment", legacy = true)
    }
    @Test fun localhostIsAcceptedOnlyForExplicitImport() {
        val old = "http://localhost:3000/$fragment"
        rejects(old)
        assertEquals("recovery", AuthCallback.parse(old, callback, allowLegacyLocalhost = true).type)
    }
    @Test fun importsChatMarkupAndEscapedAmpersandsWithoutRelaxingOriginChecks() {
        val old = "http://localhost:3000/$fragment"
        for (copied in listOf("  <$old>  ", "[Recuperar](${old.replace("&", "\\&")})", "`$old`", old.replace("&", "&amp;"))) {
            assertEquals("fake.payload.signature", AuthCallback.parse(copied, callback, true).accessToken)
        }
        rejects("[Recuperar](https://attacker.example/$fragment)", legacy = true)
    }
    @Test fun rejectsExpiredMissingAndAmbiguousTokens() {
        rejects("$callback#error=access_denied&error_code=otp_expired")
        rejects("$callback#type=recovery&token_type=bearer")
        rejects("$callback$fragment&access_token=other.payload.signature")
        rejects("$callback?access_token=query.payload.signature$fragment")
        rejects("$callback#type=magiclink&access_token=fake.payload.signature")
    }
    @Test fun confirmationDoesNotImportASession() {
        assertEquals("signup", AuthCallback.parse("$callback#type=signup", callback).type)
    }
    @Test fun originalRecoveryLinkRequiresExactConfiguredProjectAndExplicitImport() {
        val server = "https://project.supabase.co"
        val original = "$server/auth/v1/verify?token=0123456789abcdef0123456789abcdef&type=recovery&redirect_to=http%3A%2F%2Flocalhost%3A3000"
        val parsed = AuthCallback.parse(original, callback, true, server)
        assertEquals("0123456789abcdef0123456789abcdef", parsed.verificationHash)
        assertNull(parsed.accessToken)
        for ((url, allowed) in listOf(original to false, original.replace("project.supabase.co", "foreign.supabase.co") to true)) {
            try { AuthCallback.parse(url, callback, allowed, server); fail("Must reject untrusted verification link") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun originalRecoveryRejectsDuplicateAndConflictingHashes() {
        val server = "https://project.supabase.co"
        for (params in listOf("token=0123456789abcdef&token=abcdef0123456789", "token=0123456789abcdef&token_hash=abcdef0123456789")) {
            try { AuthCallback.parse("$server/auth/v1/verify?$params&type=recovery", callback, true, server); fail("Must reject ambiguous credentials") }
            catch (_: IllegalArgumentException) { }
        }
    }
    private fun rejects(link: String, legacy: Boolean = false) {
        try { AuthCallback.parse(link, callback, legacy); fail("Expected invalid link to be rejected") }
        catch (_: IllegalArgumentException) { /* Expected. Never log the link. */ }
    }
}

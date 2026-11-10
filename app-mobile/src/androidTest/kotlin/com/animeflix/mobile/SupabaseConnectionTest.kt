package com.animeflix.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animeflix.data.CloudException
import com.animeflix.data.SupabaseApi
import com.animeflix.data.SupabaseConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only smoke test against the configured project. Creates no users and sends no email. */
@RunWith(AndroidJUnit4::class)
class SupabaseConnectionTest {
    @Test fun configuredProjectSupportsEmailAndProtectsProfileTables() = runBlocking {
        val config = SupabaseConfig.bundled()
        assumeTrue("Requires a configured Supabase project", config.configured)
        config.validate()
        val api = SupabaseApi(config)
        val settings = JSONObject(api.request("/auth/v1/settings"))
        assertTrue(settings.getJSONObject("external").getBoolean("email"))
        assertFalse(settings.optBoolean("disable_signup"))
        for (table in listOf("viewing_profiles", "profile_entries")) {
            try {
                api.request("/rest/v1/$table?select=*&limit=0")
                fail("Anonymous access must be denied for $table")
            } catch (failure: CloudException) {
                assertTrue("Unexpected HTTP ${failure.status} for $table", failure.status == 401 || failure.status == 403)
            }
        }
    }
}

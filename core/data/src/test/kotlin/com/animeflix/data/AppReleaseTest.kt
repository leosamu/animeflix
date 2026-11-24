package com.animeflix.data

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun artifact(pkg: String = "com.animeflix.tv", file: String = "AnimeFlix-TV.apk", code: Long = 39, hash: String = "a".repeat(64), debug: Boolean = false) = JSONObject()
        .put("applicationId", pkg).put("file", file).put("versionCode", code).put("versionName", "0.11.1-tv-preview1").put("sha256", hash).put("debuggable", debug)
    private fun manifest(vararg items: JSONObject) = JSONObject().put("artifacts", JSONArray(items.toList()))
    @Test fun selectsPlatformAndNumericVersionIncludingPreviews() {
        val data = manifest(artifact("com.animeflix.mobile", "AnimeFlix-Mobile.apk", 38), artifact())
        assertEquals(39L, compatibleAppRelease(data, "com.animeflix.tv", 37)!!.versionCode)
        assertEquals("AnimeFlix-Mobile.apk", compatibleAppRelease(data, "com.animeflix.mobile", 36)!!.file)
    }
    @Test fun neverDowngradesOrReinstallsSameVersion() {
        assertNull(compatibleAppRelease(manifest(artifact()), "com.animeflix.tv", 39))
        assertNull(compatibleAppRelease(manifest(artifact()), "com.animeflix.tv", 41))
    }
    @Test fun rejectsWrongPackageFileHashAndDebugBuilds() {
        assertNull(compatibleAppRelease(manifest(artifact()), "com.animeflix.mobile.qa", 1))
        assertNull(compatibleAppRelease(manifest(artifact(file = "other.apk")), "com.animeflix.tv", 1))
        assertNull(compatibleAppRelease(manifest(artifact(hash = "broken")), "com.animeflix.tv", 1))
        assertNull(compatibleAppRelease(manifest(artifact(debug = true)), "com.animeflix.tv", 1))
    }
    @Test fun choosesHighestCompatibleArtifact() {
        assertEquals(101L, compatibleAppRelease(manifest(artifact(code = 99), artifact(code = 101)), "com.animeflix.tv", 9)!!.versionCode)
        assertNull(compatibleAppRelease(JSONObject(), "com.animeflix.tv", 1))
    }
}

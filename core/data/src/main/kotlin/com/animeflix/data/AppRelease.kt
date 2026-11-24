package com.animeflix.data

import org.json.JSONObject

/** Release manifests, rather than lexicographic version names, decide Android upgrade eligibility. */
data class AppRelease(val file: String, val versionCode: Long, val versionName: String, val sha256: String)

fun compatibleAppRelease(manifest: JSONObject, packageName: String, installedCode: Long): AppRelease? {
    val expected = when (packageName) {
        "com.animeflix.mobile" -> "AnimeFlix-Mobile.apk"
        "com.animeflix.tv" -> "AnimeFlix-TV.apk"
        "com.animeflix.mobile.doramas" -> "KDoramasFlix.apk"
        else -> return null
    }
    val artifacts = manifest.optJSONArray("artifacts") ?: return null
    return (0 until artifacts.length()).mapNotNull { index ->
        val artifact = artifacts.optJSONObject(index) ?: return@mapNotNull null
        val code = artifact.optLong("versionCode", -1)
        val hash = artifact.optString("sha256")
        if (artifact.optString("applicationId") != packageName || artifact.optString("file") != expected ||
            code <= installedCode || !hash.matches(Regex("[a-fA-F0-9]{64}")) || artifact.optBoolean("debuggable", true)) null
        else AppRelease(expected, code, artifact.optString("versionName"), hash)
    }.maxByOrNull { it.versionCode }
}

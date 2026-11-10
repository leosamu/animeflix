import java.util.Properties

plugins { id("com.android.library"); kotlin("android"); id("com.google.devtools.ksp") }
val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun configString(name: String) = "\"" + (localConfig.getProperty(name) ?: System.getenv(name) ?: "")
    .replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "com.animeflix.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        buildConfigField("String", "SUPABASE_URL", configString("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", configString("SUPABASE_PUBLISHABLE_KEY"))
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("org.jsoup:jsoup:1.18.3")
    api(project(":core:model"))
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}

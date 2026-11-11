plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.animeflix.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.animeflix.mobile"; minSdk = 23; targetSdk = 35; versionCode = 20; versionName = "0.10.0-preview1"
        buildConfigField("boolean", "PREVIEW_PROFILES", "false")
        resValue("string", "app_name", "AnimeFlix")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "DEMO_CATALOG", "false") }
        getByName("release") {
            buildConfigField("boolean", "DEMO_CATALOG", "false")
            if (providers.gradleProperty("testReleaseSigning").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "DEMO_CATALOG", "true")
        }
        create("cloudPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PREVIEW_PROFILES", "true")
            resValue("string", "app_name", "AnimeFlix Perfiles")
        }
    }
    testBuildType = "qa"
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core:playback"))
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(project(":core:data"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-runtime:2.7.1")
    androidTestImplementation("androidx.media3:media3-exoplayer:1.6.1")
    androidTestImplementation("androidx.media3:media3-ui:1.6.1")
}

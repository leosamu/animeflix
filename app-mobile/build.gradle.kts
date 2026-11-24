plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
val braveNative by configurations.creating { isTransitive = false }
val braveSources by configurations.creating { isTransitive = false }
val braveFiles = layout.buildDirectory.dir("generated/brave")
val prepareBrave by tasks.registering(Sync::class) {
    inputs.property("wrapperSourcePatch", 1)
    from({ zipTree(braveSources.singleFile) }) {
        include("org/mlm/adblock/**")
        into("kotlin")
        filteringCharset = "UTF-8"
        // Kotlin 2.1 requires block bodies for early returns; these failures are already caught.
        filter { line: String ->
            when (line.trim()) {
                "val bytes = nativeSerialize(nativePtr) ?: return false" ->
                    line.replace("return false", "error(\"No serialized engine\")")
                "if (!file.exists()) return false" -> line.replace(line.trim(), "check(file.exists())")
                else -> line
            }
        }
    }
    from({ zipTree(braveNative.singleFile) }) { include("jni/**", "assets/adblock/**") }
    into(braveFiles)
}
android {
    namespace = "com.animeflix.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.animeflix.mobile"; minSdk = 23; targetSdk = 35; versionCode = 38; versionName = "0.11.1-preview1"
        buildConfigField("boolean", "PREVIEW_PROFILES", "false")
        buildConfigField("boolean", "DORAMAS", "false")
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
        create("doramas") {
            initWith(getByName("debug"))
            isDebuggable = false
            applicationIdSuffix = ".doramas"
            versionNameSuffix = "-doramas-preview.11"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "DORAMAS", "true")
            buildConfigField("boolean", "PREVIEW_PROFILES", "true")
            resValue("string", "app_name", "KDoramasFlix")
        }
    }
    testBuildType = "qa"
    sourceSets.matching { it.name in setOf("debug", "release", "qa", "cloudPreview") }.configureEach {
        java.srcDir("src/anime/kotlin")
    }
    sourceSets.named("doramas") {
        java.srcDir(braveFiles.map { it.dir("kotlin") })
        jniLibs.srcDir(braveFiles.map { it.dir("jni") })
        assets.srcDir(braveFiles.map { it.dir("assets") })
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
androidComponents.beforeVariants(androidComponents.selector().withBuildType("doramas")) {
    it.minSdk = 26
}
androidComponents.onVariants(androidComponents.selector().withBuildType("doramas")) {
    it.outputs.forEach { output -> output.versionCode.set(43) }
}
tasks.matching { it.name == "preDoramasBuild" }.configureEach { dependsOn(prepareBrave) }
dependencies {
    braveNative("io.github.mlm-games:adblock:0.1.3@aar")
    braveSources("io.github.mlm-games:adblock:0.1.3:sources@jar")
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

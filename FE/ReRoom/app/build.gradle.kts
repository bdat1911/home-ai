import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

// Local development URL priority: Gradle property -> environment variable -> local.properties.
// local.properties is ignored by Git, so each developer can use their own PC LAN address.
val configuredDebugApiUrl = providers.gradleProperty("homeAiApiUrl").orNull
    ?: providers.environmentVariable("HOME_AI_API_URL").orNull
    ?: localProperties.getProperty("homeAiApiUrl")
    ?: "http://10.0.2.2:5207/"
val debugApiUrl = configuredDebugApiUrl.trim().let { url ->
    if (url.endsWith('/')) url else "$url/"
}

val releaseApiUrl = (providers.gradleProperty("homeAiReleaseApiUrl").orNull
    ?: providers.environmentVariable("HOME_AI_RELEASE_API_URL").orNull
    ?: localProperties.getProperty("homeAiReleaseApiUrl"))
    ?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith('/')) it else "$it/" }

android {
    namespace = "com.example.revroom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.revroom"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiUrl ?: "https://replace-with-production-api/"}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
}

// Gradle sync and debug builds work locally; release must never silently ship a placeholder.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val url = releaseApiUrl?.let { runCatching { URI(it) }.getOrNull() }
        require(url != null && url.scheme == "https" && !url.host.isNullOrBlank() &&
            url.host != "replace-with-production-api" && url.rawUserInfo == null &&
            url.rawQuery == null && url.rawFragment == null) {
            "Set homeAiReleaseApiUrl (or HOME_AI_RELEASE_API_URL) to your deployed HTTPS API before building release."
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Navigation & ViewModel
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // ĐÂY LÀ THƯ VIỆN QUAN TRỌNG ĐỂ HẾT LỖI ĐỎ
    implementation("androidx.compose.runtime:runtime-livedata:1.7.8")

    // Retrofit & Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Icons
    implementation(libs.androidx.compose.material.icons.extended)

    // Legacy Views (cho RecyclerView nếu cần)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

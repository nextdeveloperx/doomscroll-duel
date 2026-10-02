import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

// ---- Version: versionName "1.2.3", versionCode 1_02_03_BB. Always increasing as long as the numbers only go up.
val vMajor = providers.gradleProperty("VERSION_MAJOR").get().toInt()
val vMinor = providers.gradleProperty("VERSION_MINOR").get().toInt()
val vPatch = providers.gradleProperty("VERSION_PATCH").get().toInt()
val vBuild = (providers.gradleProperty("buildNumber").orNull ?: providers.gradleProperty("VERSION_BUILD").get()).toInt()
require(vMinor in 0..99 && vPatch in 0..99 && vBuild in 0..99) { "MINOR, PATCH and BUILD must each be 0..99 for the versionCode scheme" }
require(vMajor in 0..20) { "MAJOR must be 0..20 so versionCode stays below Google Play's 2,100,000,000 limit" }
val computedVersionCode = vMajor * 1_000_000 + vMinor * 10_000 + vPatch * 100 + vBuild

// ---- Signing. The keystore and its passwords are NEVER in git: they come from keystore.properties (git-ignored)
// or from environment variables (CI). See docs/launch-checklist.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "DUEL_KEYSTORE_FILE")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    signingValue("storePassword", "DUEL_KEYSTORE_PASSWORD") != null &&
    signingValue("keyAlias", "DUEL_KEY_ALIAS") != null &&
    signingValue("keyPassword", "DUEL_KEY_PASSWORD") != null

android {
    namespace = "com.doomscrollduel"
    compileSdk = 36

    // ---- The TURN relay for Broadcast voice (see data/broadcast/IceServers.kt). Not in git: put TURN_URLS (comma separated, e.g.
    // turn:relay.example.com:443?transport=tcp), TURN_USERNAME and TURN_CREDENTIAL in local.properties or in the environment.
    // With none set, voice still works between phones that can reach each other directly.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun turnValue(name: String): String = (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

    defaultConfig {
        resValue("string", "turn_urls", turnValue("TURN_URLS"))
        resValue("string", "turn_username", turnValue("TURN_USERNAME"))
        resValue("string", "turn_credential", turnValue("TURN_CREDENTIAL"))
        applicationId = "com.doomscrollduel"
        minSdk = 26
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = "$vMajor.$vMinor.$vPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // English and Hinglish copy lives in values/; no other locale is shipped, so no unused resources are kept.
        androidResources.localeFilters += listOf("en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "DUEL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DUEL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DUEL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Crash and analytics collection are OFF by default (manifest) and need the person's yes, in debug too.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Anything we want to silence goes in lint.xml with a written reason, not here.
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }

    // Unit tests find docs/ and functions/ (parity tests) by walking up from the module folder.
}

ksp {
    // Room writes its schema as JSON so every database change can be reviewed and migrated.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    // Voice rooms (Broadcast): peer-to-peer audio and chat between friends.
    implementation(libs.webrtc)
    implementation(libs.googleid)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.billing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// A release bundle must never be made unsigned by accident: stop with a clear message instead.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.name == "bundleRelease" || it.name == "assembleRelease" || it.name == "packageRelease" }
    if (wantsRelease && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not set up. Create keystore.properties (see keystore.properties.example) " +
                "or set DUEL_KEYSTORE_FILE, DUEL_KEYSTORE_PASSWORD, DUEL_KEY_ALIAS and DUEL_KEY_PASSWORD.",
        )
    }
}

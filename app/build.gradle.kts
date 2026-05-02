import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun resolveMapsKey(): String {
    val fromLocal = localProps.getProperty("MAPS_API_KEY")
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromEnv = System.getenv("MAPS_API_KEY")
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return "MISSING_MAPS_API_KEY"
}

val runNumber: String = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
val gitSha: String = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
val appVersionCode: Int = runNumber.toIntOrNull() ?: 1
val appVersionName: String = "0.1.$runNumber+$gitSha"

android {
    namespace = "xyz.geocam.vps"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.geocam.vps"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        manifestPlaceholders["MAPS_API_KEY"] = resolveMapsKey()

        buildConfigField("String", "GITHUB_OWNER", "\"msuther898\"")
        buildConfigField("String", "GITHUB_REPO", "\"geocam-vps-android\"")
        buildConfigField("String", "VERSION_NAME_SHORT", "\"0.1.$runNumber\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            val stableKs = rootProject.file("keystore/debug.keystore")
            if (stableKs.exists()) {
                storeFile = stableKs
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.arcore)

    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
}

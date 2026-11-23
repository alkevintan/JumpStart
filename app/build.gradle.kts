plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    enableStrongSkippingMode = true
}

android {
    namespace = "com.aktivesolutions.jumpstart"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aktivesolutions.jumpstart"
        minSdk = 25
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.9"
    }

    // CI decodes a private release keystore from GitHub Secrets into RELEASE_KEYSTORE. Using a stable key is
    // what makes in-app OTA upgrades install cleanly: Android rejects an update whose signature differs from
    // the installed app. Local builds have none of these env vars and keep using the debug keystore.
    val ciKeystore = System.getenv("RELEASE_KEYSTORE")
    signingConfigs {
        if (ciKeystore != null) {
            create("release") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug stays a plain, fast-building, debuggable build (no R8).
        debug {
            isMinifyEnabled = false
        }
        // Release = what gets installed on devices: not debuggable, R8 + resource shrinking (material-icons-extended
        // is huge unshrunk and the SM-T550 has 1.5 GB RAM).
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
}

dependencies {
    // Installs the baseline profile (src/main/baseline-prof.txt) on first run so the app is AOT-compiled without warm-up.
    implementation(libs.profileinstaller)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    testImplementation(libs.junit)
}

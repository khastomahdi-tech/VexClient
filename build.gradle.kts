plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val useReleaseKey = !System.getenv("KEYSTORE_PATH").isNullOrEmpty()

android {
    namespace = "com.vexclient.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vexclient.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        create("release") {
            if (useReleaseKey) {
                storeFile = file(System.getenv("KEYSTORE_PATH"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (useReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Xray core (libv2ray.aar) is optional. Place it in app/libs to enable real connections.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}

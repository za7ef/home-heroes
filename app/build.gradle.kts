plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksFile = System.getenv("HH_KEYSTORE")

android {
    namespace = "com.za7ef.homeheroes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.za7ef.homeheroes"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storeType = "pkcs12"
                storePassword = System.getenv("HH_KS_PASS")
                keyAlias = "homeheroes"
                keyPassword = System.getenv("HH_KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

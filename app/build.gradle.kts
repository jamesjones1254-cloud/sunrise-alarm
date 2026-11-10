plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.jamesjones.sunrise"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jamesjones.sunrise"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Personal signing key kept in the (private) repo so every build can update the last one in place.
    signingConfigs {
        create("personal") {
            storeFile = file("sunrise.keystore")
            storePassword = "sunrise-alarm"
            keyAlias = "sunrise"
            keyPassword = "sunrise-alarm"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

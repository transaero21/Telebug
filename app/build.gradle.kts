import java.util.Properties

val signingProps = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ru.transaero21.telebug"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProps.getProperty("signing.store.file"))
            storePassword = signingProps.getProperty("signing.store.password")
            keyAlias = signingProps.getProperty("signing.key.alias")
            keyPassword = signingProps.getProperty("signing.key.password")
        }
    }

    defaultConfig {
        applicationId = "ru.transaero21.telebug"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(libs.xposed.api)
}
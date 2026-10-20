plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.risqi.nativeports.feedingfrenzy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.risqi.nativeports.feedingfrenzy"
        minSdk = 28 // Android 9.0 (Pie) +
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0-oprek"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/main/kotlin")
        }
    }

    androidResources {
        noCompress += listOf("pak", "wav", "ogg", "mp3", "json")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}

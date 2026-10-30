plugins {
    id("com.android.application")
}

android {
    namespace = "com.bornoutofcuriosity.radiogardentv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bornoutofcuriosity.radiogardentv"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal sideload build: sign with the debug key so it installs without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

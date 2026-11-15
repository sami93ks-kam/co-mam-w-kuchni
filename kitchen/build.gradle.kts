plugins {
    id("com.android.application")
}

android {
    namespace = "pl.kamilitsolution.kuchnia"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.kamilitsolution.kuchnia"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "pl.kamilitsolution.kuchnia.KitchenFlowTest"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

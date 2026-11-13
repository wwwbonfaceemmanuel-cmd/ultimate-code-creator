import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionNameOverride = (project.findProperty("versionNameOverride") as String?)
    ?: System.getenv("VERSION_NAME")
    ?: "0.3.0"
val versionCodeOverride = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull()
    ?: System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: 3

android {
    namespace = "com.ultimate.codecreator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ultimate.codecreator"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOverride
        versionName = versionNameOverride
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
        val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
        val signingKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
        val signingKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
        if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() && !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

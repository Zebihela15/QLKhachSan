plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.sinhvien.appqlkhachsan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sinhvien.appqlkhachsan"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    buildToolsVersion = "35.0.0"
}

dependencies {
    // UI & AndroidX
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.cardview)
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    // Room
    implementation(libs.room.runtime)
    implementation(libs.firebase.inappmessaging)
    annotationProcessor(libs.room.compiler)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Firebase (BoM + Modules)
    implementation(platform(libs.firebase.bom))
implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.inappmessaging)

    // Lifecycle
    implementation(libs.lifecycle.livedata)
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

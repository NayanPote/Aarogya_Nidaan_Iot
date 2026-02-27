plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.healthcare.aarogyanidaan"
    compileSdk = 34  // Latest stable SDK

    defaultConfig {
        applicationId = "com.healthcare.aarogyanidaan"
        minSdk = 30  // Supports Android 11+
        targetSdk = 34  // Targets latest Android 14
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core Components
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")  // Compatible with API 34
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:23.3.1")

// Firebase Cloud Functions
    implementation("com.google.firebase:firebase-functions:20.4.0")

// AndroidX Core (compatible with Android 11)
    implementation("androidx.core:core:1.6.0")

    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

//    implementation ("com.squareup.okhttp3:okhttp:4.9.3")

    // Optional: Supabase Android SDK (if you want to use more Supabase features)
//    implementation ("io.github.supabase:supabase-android:0.4.0")
//    implementation ("io.github.supabase:postgrest-android:0.4.0")
//    implementation ("io.github.supabase:storage-android:0.4.0")

    // Google Material UI
    implementation("com.google.android.material:material:1.9.0")

    implementation ("com.android.volley:volley:1.2.1") // For network requests
    implementation ("org.jsoup:jsoup:1.15.3") // For parsing HTML if needed
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation ("de.hdodenhof:circleimageview:3.1.0")

    // Google Maps & Location Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.0")

    // Networking & API
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Image Loading
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation(libs.activity)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.navigation.runtime)
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")

    implementation ("com.google.firebase:firebase-firestore:24.10.0")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("org.tensorflow:tensorflow-lite:2.16.1")

    // Animation
    implementation("com.airbnb.android:lottie:5.0.3")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")

    // Google Authentication
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.0.1")

    // Other Utilities
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("com.google.maps:google-maps-services:2.1.2")
    implementation("org.osmdroid:osmdroid-android:6.1.10")

    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")

    platform("com.google.firebase:firebase-bom:32.7.0")
    implementation ("com.google.firebase:firebase-auth")
    implementation ("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-functions")

    implementation ("com.google.android.material:material:1.10.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("androidx.cardview:cardview:1.0.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

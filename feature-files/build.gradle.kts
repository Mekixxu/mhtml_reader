plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.html_reader.feature.files"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        val main by getting
        main.java.setSrcDirs(
            listOf(
                "../core/fileops",
                "../core/files",
                "../core/session/repo",
                "../core/session/di"
            )
        )
    }
}

dependencies {
    implementation(project(":core-base"))
    implementation(project(":core-storage"))
    implementation(project(":core-data"))
    implementation(project(":core-domain"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.52")
    implementation("javax.inject:javax.inject:1")
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.html_reader.core.domain"
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
                "../core/cache",
                "../core/domain",
                "../core/title",
                "../core/backup"
            )
        )
    }
}

dependencies {
    implementation(project(":core-base"))
    implementation(project(":core-storage"))
    implementation(project(":core-data"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.dagger:hilt-android:2.52")
    implementation("javax.inject:javax.inject:1")
}

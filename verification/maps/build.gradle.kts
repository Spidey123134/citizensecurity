plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.citizensecurity.verification.maps"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.example.citizensecurity.verification.maps"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "0.4.0a"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["class"] =
            "com.example.citizensecurity.verification.maps.NativeMapsSuite"
    }
    // Reutiliza las fuentes originales, sin depender del APK ni copiar adaptadores.
    sourceSets.named("main") {
        kotlin.directories.add(
            rootProject.file("app/src/main/kotlin/com/example/citizensecurity/maps").absolutePath,
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(libs.maplibre)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

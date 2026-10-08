plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.citizensecurity.verification.location"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.example.citizensecurity.verification.location"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "0.4.0a"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "permission"
    productFlavors {
        create("physical") {
            dimension = "permission"
            applicationIdSuffix = ".physical"
        }
        create("precise") {
            dimension = "permission"
            applicationIdSuffix = ".precise"
            testInstrumentationRunnerArguments["class"] =
                "com.example.citizensecurity.verification.location.LocationPermissionNativeTest"
        }
        create("approximate") {
            dimension = "permission"
            applicationIdSuffix = ".approximate"
            testInstrumentationRunnerArguments["class"] =
                "com.example.citizensecurity.verification.location.LocationPermissionNativeTest"
        }
        create("denied") {
            dimension = "permission"
            applicationIdSuffix = ".denied"
            testInstrumentationRunnerArguments["class"] =
                "com.example.citizensecurity.verification.location.LocationPermissionNativeTest"
        }
        create("flow") {
            dimension = "permission"
            applicationIdSuffix = ".flow"
            testInstrumentationRunnerArguments["class"] =
                "com.example.citizensecurity.verification.location.LocationFlowAndroidSuite"
        }
    }
    // Compila las fuentes originales: no copia código ni depende del APK productivo.
    sourceSets.named("main") {
        kotlin.directories.add(
            rootProject.file("app/src/main/kotlin/com/example/citizensecurity/maps").absolutePath,
        )
        kotlin.directories.add(
            rootProject.file("app/src/main/kotlin/com/example/citizensecurity/report").absolutePath,
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
    constraints {
        implementation(libs.androidx.fragment) {
            because("El contrato Activity Result requiere Fragment compatible con MapLibre.")
        }
    }
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

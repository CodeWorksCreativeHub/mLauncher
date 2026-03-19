import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// =========================
//   Version configuration
// =========================

val major = 1
val minor = 11
val patch = 4
val build = 3

val baseVersionName = "$major.$minor.$patch Build $build"

val baseVersionCode =
    (String.format("%02d", major) +
            String.format("%02d", minor) +
            String.format("%02d", patch) +
            String.format("%02d", build)).toInt()

// =========================
//   Android configuration
// =========================

extensions.configure<ApplicationExtension>("android") {

    namespace = "com.github.codeworkscreativehub.mlauncher"

    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = baseVersionName
    }

    flavorDimensions += "channel"

    productFlavors {
        create("prod") {
            dimension = "channel"
            applicationId = "app.mlauncher"
            resValue("string", "app_name", "Multi Launcher")
        }

        create("beta") {
            dimension = "channel"
            applicationId = "app.mlauncher.beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "Multi Launcher Beta")
        }

        create("alpha") {
            dimension = "channel"
            applicationId = "app.mlauncher.alpha"
            versionNameSuffix = "-alpha"
            resValue("string", "app_name", "Multi Launcher Alpha")
        }

        create("nightly") {
            dimension = "channel"
            applicationId = "app.mlauncher.nightly"
            versionNameSuffix = "-nightly"
            resValue("string", "app_name", "Multi Launcher Nightly")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("app/mLauncher.jks")

            println("Using keystore: ${keystoreFile.absolutePath} (${keystoreFile.length()} bytes)")

            storeFile = keystoreFile
            storePassword = System.getenv("KEY_STORE_PASSWORD")
                ?: error("KEY_STORE_PASSWORD not set")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: error("KEY_ALIAS not set")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: error("KEY_PASSWORD not set")
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs["release"]

            resValue("string", "app_version", baseVersionCode.toString())
            resValue("string", "app_name", "Multi Launcher Debug")
            resValue("string", "empty", "")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs["release"]

            resValue("string", "app_version", baseVersionName)
            resValue("string", "empty", "")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("libandroidx.graphics.path.so")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}


// =========================
//   KSP
// =========================

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// =========================
//   Dependencies
// =========================

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.activity.ktx)
    implementation(libs.palette.ktx)
    implementation(libs.material)
    implementation(libs.viewpager2)
    implementation(libs.activity)
    implementation(libs.commons.text)

    implementation(libs.lifecycle.extensions)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.work.runtime.ktx)

    implementation(libs.constraintlayout)
    implementation(libs.constraintlayout.compose)
    implementation(libs.activity.compose)

    implementation(libs.compose.material)
    implementation(libs.compose.android)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)

    implementation(libs.biometric.ktx)

    implementation(libs.moshi)
    implementation(libs.moshi.ktx)
    ksp(libs.moshi.codegen)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    implementation(libs.espresso.idling.resource)

    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    implementation(libs.test.core.ktx)

    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)

    debugImplementation(libs.fragment.testing)
    androidTestImplementation(libs.navigation.testing)

    testImplementation(libs.junit)
}

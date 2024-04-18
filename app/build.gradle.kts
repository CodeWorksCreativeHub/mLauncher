@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application") apply true
    id("kotlin-android") apply true
    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics") apply true
}

android {
    namespace = "com.github.droidworksstudio.mlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.mlauncher"
        minSdk = 23
        targetSdk = 34
        versionCode = 154
        versionName = "1.5.4"
        buildConfigField("String", "FIREBASE_APP_ID", "\"${System.getenv("FIREBASE_APP_ID") ?: "DEFAULT_APP_ID"}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${System.getenv("FIREBASE_API_KEY") ?: "DEFAULT_APP_ID"}\"")
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "mLauncher Debug")
        }

        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            resValue("string", "app_name", "mLauncher")
        }
    }

    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                if (output?.outputFileName?.endsWith(".apk") == true) {
                    output.outputFileName =
                        "${defaultConfig.applicationId}_v${defaultConfig.versionName}-Signed.apk"
                }
            }
        }
        if (buildType.name == "debug") {
            outputs.all {
                val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                if (output?.outputFileName?.endsWith(".apk") == true) {
                    output.outputFileName =
                        "${defaultConfig.applicationId}_v${defaultConfig.versionName}-Debug.apk"
                }
            }
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    val androidxTestKotlin = "1.6.5"
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Android lifecycle
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")

    // Work Manager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // Text similarity
    implementation("org.apache.commons:commons-text:1.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))

    // Add the dependencies for the Crashlytics NDK and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.firebase:firebase-analytics")

    // JETPACK
    // Integration with activities
    //noinspection GradleDependency
    implementation("androidx.activity:activity-compose:1.8.2")
    // Compose Material Design
    implementation("androidx.compose.material:material:$androidxTestKotlin")
    implementation("com.github.SmartToolFactory:Compose-Colorful-Sliders:1.2.0")
    // Animations
    implementation("androidx.compose.animation:animation:$androidxTestKotlin")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // AndroidX
    implementation("androidx.compose.ui:ui:$androidxTestKotlin")
    implementation("androidx.compose.ui:ui-tooling:$androidxTestKotlin")
    implementation("androidx.compose.foundation:foundation:$androidxTestKotlin")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")

    val acraVersion = "5.11.3"
    implementation("ch.acra:acra-core:$acraVersion")
    implementation("ch.acra:acra-toast:$acraVersion")

    val androidxTestEspresso = "3.5.1"
    androidTestImplementation("androidx.test.espresso:espresso-core:$androidxTestEspresso")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$androidxTestEspresso")
    implementation("androidx.test.espresso:espresso-idling-resource:$androidxTestEspresso")
    implementation("androidx.test.espresso:espresso-idling-resource:$androidxTestEspresso")

    // Test rules and transitive dependencies:
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$androidxTestKotlin")
    // Needed for createComposeRule, but not createAndroidComposeRule:
    debugImplementation("androidx.compose.ui:ui-test-manifest:$androidxTestKotlin")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.7")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")
    implementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal() // for plugins
        mavenCentral()       // for Kotlin stdlib
        google()             // for Android dependencies
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // for Android dependencies
        gradlePluginPortal() // for plugins
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        mavenCentral {
            metadataSources {
                mavenPom()
                artifact()
            }
        } // for Kotlin stdlib
    }
}

include(":app")
rootProject.name = "mLauncher"
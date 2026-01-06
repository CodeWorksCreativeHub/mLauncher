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
        mavenCentral {
            metadataSources {
                mavenPom()
                artifact()
            }
        } // for Kotlin stdlib
        maven("https://jitpack.io")
    }
}

include(":app")
rootProject.name = "mLauncher"
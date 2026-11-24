package com.sdui.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Non-UI SDK module: pure Kotlin logic packaged as an Android library. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureAndroidKotlin(this)
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }

        dependencies {
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
        }
    }
}

/** SDK module that ships Composables. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("sdui.android.library")

        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}

/** Consumer application. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureAndroidKotlin(this)
            configureCompose(this)
            defaultConfig.targetSdk = libs.int("targetSdk")
        }
    }
}

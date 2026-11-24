plugins {
    id("sdui.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.core" }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
}

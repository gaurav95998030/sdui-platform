plugins {
    id("sdui.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.cache" }

dependencies {
    api(project(":sdui-core"))
}

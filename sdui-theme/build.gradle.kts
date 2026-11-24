plugins {
    id("sdui.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.theme" }

dependencies {
    api(project(":sdui-core"))
}

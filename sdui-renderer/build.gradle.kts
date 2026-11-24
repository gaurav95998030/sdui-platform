plugins {
    id("sdui.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.renderer" }

dependencies {
    api(project(":sdui-core"))
    api(project(":sdui-state"))
    api(project(":sdui-theme"))
    api(project(":sdui-actions"))
    implementation(project(":sdui-analytics"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}

plugins {
    id("sdui.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.components" }

dependencies {
    api(project(":sdui-renderer"))
    implementation(project(":sdui-analytics"))
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons)
}

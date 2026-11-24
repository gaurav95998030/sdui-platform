plugins {
    id("sdui.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.actions" }

dependencies {
    api(project(":sdui-core"))
    api(project(":sdui-navigation"))
    api(project(":sdui-state"))
    implementation(project(":sdui-network"))
    implementation(project(":sdui-analytics"))
    implementation(libs.androidx.core.ktx)
}

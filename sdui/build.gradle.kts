plugins {
    id("sdui.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui" }

dependencies {
    api(project(":sdui-core"))
    api(project(":sdui-renderer"))
    api(project(":sdui-components"))
    api(project(":sdui-actions"))
    api(project(":sdui-navigation"))
    api(project(":sdui-network"))
    api(project(":sdui-cache"))
    api(project(":sdui-analytics"))
    api(project(":sdui-theme"))
    api(project(":sdui-state"))
    api(project(":sdui-parser"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}

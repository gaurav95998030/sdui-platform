plugins {
    id("sdui.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sdui.network" }

dependencies {
    api(project(":sdui-core"))
    api(project(":sdui-cache"))
    implementation(project(":sdui-state"))
    // HttpAnalyticsSink ships the SDK's automatic events to the backend.
    api(project(":sdui-analytics"))
    // api: OkHttpClientFactory and SduiInterceptor expose OkHttp types, and hosts
    // are expected to share their own client with the SDK.
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.sdui.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("sduiAndroidLibrary") {
            id = "sdui.android.library"
            implementationClass = "com.sdui.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("sduiAndroidLibraryCompose") {
            id = "sdui.android.library.compose"
            implementationClass = "com.sdui.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("sduiAndroidApplication") {
            id = "sdui.android.application"
            implementationClass = "com.sdui.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}

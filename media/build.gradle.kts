plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
}

dependencies {
    api(libs.okhttp)
    implementation("org.json:json:20240303")
    implementation(libs.kotlinx.serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

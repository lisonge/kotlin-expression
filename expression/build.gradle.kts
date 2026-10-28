@file:OptIn(
    org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
    org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class,
)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
    jvm { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
    if (!providers.gradleProperty("jvmOnly").isPresent) {
        js().nodejs()
        wasmJs().nodejs()
        wasmWasi().nodejs()
        iosArm64()
        iosSimulatorArm64()
        linuxArm64()
        linuxX64()
        macosArm64()
        tvosArm64()
        tvosSimulatorArm64()
        watchosArm64()
        watchosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
    abiValidation()
}

mavenPublishing {
    if (project.providers.gradleProperty("signing.keyId").isPresent) {
        publishToMavenCentral()
        signAllPublications()
    }
    coordinates(group.toString(), "expression", version.toString())
    pom {
        name.set("Kotlin Expression")
        description.set("A Kotlin Multiplatform mathematical expression library")
        url.set("https://github.com/lisonge/kotlin-expression")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                name.set("lisonge")
                email.set("i@songe.li")
                url.set("https://github.com/lisonge")
            }
        }
        scm { url.set("https://github.com/lisonge/kotlin-expression") }
    }
}

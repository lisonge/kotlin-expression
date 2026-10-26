import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jmh)
}

kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":expression"))
}

val smoke = providers.gradleProperty("benchmarkSmoke").isPresent

tasks.named("jmh") {
    outputs.upToDateWhen { false }
}

jmh {
    jmhVersion.set("1.37")
    includeTests.set(false)
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ns")
    threads.set(1)
    fork.set(if (smoke) 1 else 2)
    warmupIterations.set(if (smoke) 1 else 5)
    iterations.set(if (smoke) 1 else 5)
    warmup.set(if (smoke) "200ms" else "1s")
    timeOnIteration.set(if (smoke) "200ms" else "1s")
    jvmArgs.set(listOf("-Xms512m", "-Xmx512m"))
    profilers.set(listOf("gc"))
    failOnError.set(true)
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("reports/jmh/${if (smoke) "smoke" else "results"}.json"))
    humanOutputFile.set(layout.buildDirectory.file("reports/jmh/${if (smoke) "smoke" else "results"}.txt"))
}

plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
}

allprojects {
    group = "li.songe.expression"
    version = "0.1.2" + if (!providers.environmentVariable("CI").isPresent) "-SNAPSHOT" else ""
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose)
  alias(libs.plugins.shadow)
}

group = "team.elrant"
version = "0.0.1-devel-kt"

repositories {
  mavenLocal()
  mavenCentral()
  google()
}

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(libs.bundles.serialization)
  implementation(libs.bundles.logging)
  implementation(libs.bundles.xmpp)
  implementation(libs.bundles.coroutines)
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_25
  }
}

compose.desktop {
  application {
    mainClass = "team.elrant.bubbles.MainKt"
  }
}

tasks.withType<AbstractArchiveTask> {
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.jar {
  manifest.attributes["Main-Class"] = "team.elrant.bubbles.MainKt"
}

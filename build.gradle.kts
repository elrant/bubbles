import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose)
  id("com.gradleup.shadow") version "9.3.0"
}

group = "team.elrant"
version = "0.0.0-devel"

repositories {
  mavenLocal()
  mavenCentral()
  google()
}

dependencies {
  implementation(libs.bundles.annotations)
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)

  arrayOf(
    libs.bundles.serialization,
    libs.bundles.logging,
    libs.bundles.xmpp,
    libs.bundles.coroutines,
  ).forEach { implementation(it); shadow(it) }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_25
  }
}

compose.desktop {
  application {
    mainClass = "MainKt"
  }
}

tasks.withType<AbstractArchiveTask> {
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.jar {
  manifest.attributes["Main-Class"] = "MainKt"
}

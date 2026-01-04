import java.nio.charset.StandardCharsets

plugins {
  id("java")
  id("com.gradleup.shadow") version "9.3.0"
}

group = "team.elrant"
version = "0.0.0-devel"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  annotationProcessor(libs.bundles.annotations); implementation(libs.bundles.annotations);
  annotationProcessor(libs.annotations.get())
  implementation(libs.annotations.get())

  arrayOf(
    libs.bundles.serialization,
    libs.bundles.logging,
    libs.bundles.xmpp,
    libs.bundles.themes,
  ).forEach { implementation(it); shadow(it) }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_21.toString()
  targetCompatibility = JavaVersion.VERSION_21.toString()
  options.encoding = StandardCharsets.UTF_8.toString()
}

tasks.withType<AbstractArchiveTask> {
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

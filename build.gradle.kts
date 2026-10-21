plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose)
  alias(libs.plugins.shadow)
  alias(libs.plugins.detekt)
}

group = "team.elrant"
version = "0.0.1-devel-kt"

val mainClassName = "team.elrant.bubbles.MainKt"

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

  detektPlugins(libs.detekt.ktlint)
  detektPlugins(libs.compose.rules.detekt)
}

// JetBrains Runtime is required everywhere (compile and run): the app's `--wayland` mode needs its
// native Wayland toolkit (see Main.kt). It is not auto-provisioned; CI and dev machines must have a
// JBR 25 installed where Gradle can detect it.
val jbr: Action<JavaToolchainSpec> = Action {
  languageVersion = JavaLanguageVersion.of(25)
  vendor = JvmVendorSpec.JETBRAINS
}

kotlin {
  jvmToolchain(jbr)
}

// Static analysis: `./gradlew detekt`, also run by `check`. Rules are detekt's defaults plus Compose rules,
// with overrides in config/detekt/detekt.yml.
detekt {
  buildUponDefaultConfig = true
  allRules = true
  config.setFrom(file("config/detekt/detekt.yml"))
}

tasks.withType<AbstractArchiveTask> {
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

// The runnable fat JAR is the project's JAR: build/libs/bubbles-<version>.jar, built by `jar`,
// `assemble` and `build`.
tasks.shadowJar {
  archiveClassifier = ""
  manifest.attributes["Main-Class"] = mainClassName
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}

/** Runs the app JAR. `--x11` / `--wayland` are forwarded to the app; `--args` passes anything else. */
abstract class RunBubbles : JavaExec() {
  @get:Input
  @get:Option(option = "x11", description = "Run through X11 / Xwayland with GPU rendering (default).")
  abstract val x11: Property<Boolean>

  @get:Input
  @get:Option(option = "wayland", description = "Run as a native Wayland window (CPU rendering).")
  abstract val wayland: Property<Boolean>

  init {
    x11.convention(false)
    wayland.convention(false)
  }

  @TaskAction
  override fun exec() {
    if (x11.get()) args("--x11")
    if (wayland.get()) args("--wayland")
    super.exec()
  }
}

tasks.register<RunBubbles>("run") {
  group = "application"
  description = "Builds the JAR and runs it on JetBrains Runtime."
  classpath(tasks.shadowJar)
  mainClass = mainClassName
  javaLauncher = javaToolchains.launcherFor(jbr)
  standardInput = System.`in`
}

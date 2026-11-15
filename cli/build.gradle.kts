import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    kotlin("jvm")
    application
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

repositories { mavenCentral() }
kotlin { jvmToolchain(25) }
application {
    applicationName = "adocs"
    mainClass.set("xyz.robinjoon.adocs.MainKt")
}
dependencies {
    implementation("tools.jackson.core:jackson-databind:3.1.5")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    detektPlugins(project(":quality-rules"))
}
ktlint { version.set("1.8.0") }
detekt {
    toolVersion.set("2.0.0-alpha.6")
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig.set(false)
    ignoreFailures.set(false)
    failOnSeverity.set(FailOnSeverity.Error)
}
tasks.withType<Detekt>().configureEach {
    baseline.unset()
    baseline.unsetConvention()
}
tasks.named<Detekt>("detektTest") {
    config.setFrom(rootProject.files("config/detekt/detekt.yml", "config/detekt/test.yml"))
    classpath.setFrom(configurations.testCompileClasspath, sourceSets.main.get().output, sourceSets.test.get().output)
    friendPaths.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs,
    )
}
tasks.named<Detekt>("detekt") {
    setSource(files())
    dependsOn("detektMain", "detektTest")
}
tasks.withType<KtLintCheckTask>().configureEach { dependsOn(tasks.ktlintFormat) }
tasks.withType<KotlinCompile>().configureEach { dependsOn(tasks.ktlintCheck) }
tasks.withType<Test>().configureEach {
    dependsOn(tasks.ktlintCheck, "detektMain", "detektTest", ":quality-rules:test")
    useJUnitPlatform()
}
tasks.check { dependsOn("detektMain", "detektTest", ":quality-rules:test") }

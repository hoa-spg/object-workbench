import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "at.destion.object-workbench"

val versionFile = file("plugin-version.properties")
val requestedTasks = gradle.startParameter.taskNames.map {
    it.substringAfterLast(':')
}
val isBuildRun = requestedTasks.any { it == "build" || it == "buildPlugin" }

val props = Properties().apply {
    if (versionFile.exists()) {
        versionFile.inputStream().use { load(it) }
    }
}

val currentVersion = props.getProperty("version", "0.1.0")
val parts = currentVersion.split('.')
val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

val effectiveVersion = if (isBuildRun) {
    val bumped = "$major.$minor.${patch + 1}"
    props.setProperty("version", bumped)
    versionFile.outputStream().use { props.store(it, null) }
    bumped
} else {
    "$major.$minor.$patch"
}

version = effectiveVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}

dependencies {
    intellijPlatform {
        local("/opt/jetbrains/intellij-idea")
        bundledPlugin("com.intellij.java")
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

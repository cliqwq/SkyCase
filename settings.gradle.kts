rootProject.name = "skycase"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.teamresourceful.com/repository/maven-public/")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("dev.kikugie.stonecutter") version "0.10-alpha.2"
}

val mcVersions = listOf("26.2")

stonecutter {
    create(rootProject) {
        mcVersions.forEach { version(it) }
        vcsVersion = mcVersions.first()
    }
}

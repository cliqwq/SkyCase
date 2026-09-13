plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
    kotlin("jvm") version "2.4.0"
}

version = property("version") as String
group = property("maven_group") as String

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.teamresourceful.com/repository/maven-public/")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
        content { includeGroup("me.djtheredstoner") }
    }
    maven("https://repo.hypixel.net/repository/Hypixel") {
        content { includeGroup("net.hypixel") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

val minecraftVersionAttribute: Attribute<String> = Attribute.of("net.minecraft.version", String::class.java)

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${libs.versions.fabric.language.kotlin.get()}")

    implementation("tech.thatgravyboat:skyblock-api:${libs.versions.skyblockapi.get()}") {
        attributes {
            attribute(minecraftVersionAttribute, property("skyblockapi_mc_version") as String)
        }
    }

    // Required at runtime by skyblock-api / hypixel-mod-api; not in the brief's dependency
    // list, but the mod fails to load without it (see task-1-report.md).
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_range" to project.property("minecraft_range"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

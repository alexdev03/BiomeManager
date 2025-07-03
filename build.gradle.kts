plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven(url="https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot
    maven(url="https://repo.dmulloy2.net/repository/public/") // ProtocolLib
    maven(url="https://maven.enginehub.org/repo/") // WorldEdit
    maven("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2") // mechanicscore 4.x


}

dependencies {
//    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
//    compileOnly("com.cjcrafter:mechanicscore:4.0.4")
    compileOnly(files(fileTree("libs")))
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.13")

    // adventure
    compileOnly("net.kyori:adventure-api:4.15.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.2")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.15.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.15.0")

    implementation("org.bstats:bstats-bukkit:3.0.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(21)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
}
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "BiomeManager"

include(":BuildBiomeManager")

//include(":Biome_1_19_R3")
//include(":Biome_1_20_R1")
//include(":Biome_1_20_R2")
//include(":Biome_1_20_R3")
//include(":Biome_1_20_R4")
//include(":Biome_1_21_R1")
include(":Biome_1_21_4")
include(":Biome_1_21_7")

//project(":Biome_1_19_R3").projectDir = file("Compatibility/Biome_1_19_R3")
//project(":Biome_1_20_R1").projectDir = file("Compatibility/Biome_1_20_R1")
//project(":Biome_1_20_R2").projectDir = file("Compatibility/Biome_1_20_R2")
//project(":Biome_1_20_R3").projectDir = file("Compatibility/Biome_1_20_R3")
//project(":Biome_1_20_R4").projectDir = file("Compatibility/Biome_1_20_R4")
//project(":Biome_1_21_R1").projectDir = file("Compatibility/Biome_1_21_R1")
project(":Biome_1_21_4").projectDir = file("Compatibility/Biome_1_21_4")
project(":Biome_1_21_7").projectDir = file("Compatibility/Biome_1_21_7")

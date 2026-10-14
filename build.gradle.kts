plugins {
    id("fabric-loom") version "1.17.21"
    kotlin("jvm") version "2.2.20"
}

version = property("mod_version") as String
group = property("maven_group") as String

repositories {
    maven("https://maven.parchmentmc.org")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.wispforest.io/releases") { content { includeGroupAndSubgroups("io.wispforest") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("minecraft_version")}:${property("parchment_version")}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")
    modCompileOnly("com.terraformersmc:modmenu:11.0.3")

    if (providers.gradleProperty("megaShowdown").isPresent) {
        modLocalRuntime("maven.modrinth:cobblemon-mega-showdown:1.2.0+1.8.1+1.21.1-release")
        modLocalRuntime("maven.modrinth:architectury-api:13.0.11+fabric")

        modLocalRuntime("maven.modrinth:accessories:Xlt4eWBe")
        modLocalRuntime("maven.modrinth:owo-lib:0.13.0-alpha.15+1.21")

        modLocalRuntime("io.wispforest:endec:0.1.5.1")
        modLocalRuntime("io.wispforest.endec:gson:0.1.3.1")
        modLocalRuntime("io.wispforest.endec:netty:0.1.2")
        modLocalRuntime("io.wispforest.endec:jankson:0.1.3.1")
        modLocalRuntime("blue.endless:jankson:1.2.2")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

loom {
    runs {
        create("client2") {
            client()
            runDir = "run2"
            programArg("--username")
            programArg("Dev2")
        }
    }
}

tasks.withType<JavaCompile> {
    options.release = 21
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.test {
    useJUnitPlatform()
}

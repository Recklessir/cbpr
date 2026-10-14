package com.cbpr.config

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files

object Settings {
    private val log = LoggerFactory.getLogger("cbpr")

    @Volatile
    var current = CbprConfig()
        private set

    fun apply(config: CbprConfig) {
        current = config
    }

    fun save() = write(current)

    fun load() {
        val path = FabricLoader.getInstance().configDir.resolve("cbpr.json")
        val text = try {
            if (Files.exists(path)) Files.readString(path) else null
        } catch (e: IOException) {
            log.warn("CBPR could not read {}, using defaults", path, e)
            null
        }
        current = CbprConfig.parse(text) { log.warn("CBPR config {}: {}", path, it) }
        if (text == null) {
            write(current)
        }
    }

    private fun write(config: CbprConfig) {
        val path = FabricLoader.getInstance().configDir.resolve("cbpr.json")
        try {
            Files.writeString(path, config.toJson())
        } catch (e: IOException) {
            log.warn("CBPR could not write the config to {}", path, e)
        }
    }
}

package com.cbpr.battle

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

object ProfileLoader : SimpleSynchronousResourceReloadListener {
    private val log = LoggerFactory.getLogger("cbpr")

    @Volatile
    var profiles = ProfileSet(listOf(MoveProfile.GENERIC))
        private set

    override fun getFabricId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cbpr", "move_profiles")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val loaded = ArrayList<MoveProfile>()
        val files = manager.listResources("move_profiles") { it.namespace == "cbpr" && it.path.endsWith(".json") }
        for ((location, resource) in files) {
            try {
                resource.openAsReader().use { reader ->
                    val json = JsonParser.parseReader(reader)
                    if (!json.isJsonObject) {
                        log.warn("CBPR profile {} is not a JSON object, skipping it", location)
                        return@use
                    }
                    MoveProfile.parse(json.asJsonObject) { log.warn("CBPR profile {}: {}", location, it) }?.let(loaded::add)
                }
            } catch (e: Exception) {
                log.warn("CBPR could not read profile {}, skipping it", location, e)
            }
        }
        profiles = ProfileSet(loaded.ifEmpty { listOf(MoveProfile.GENERIC) })
    }
}

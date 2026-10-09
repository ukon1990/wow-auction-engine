package net.jonasmf.auctionengine.config

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

object JsonMappers {
    /** Auction dumps, DB JSON columns and S3 objects: Jackson 2 defaults keep the stored format unchanged. */
    val storage: JsonMapper = JsonMapper.builderWithJackson2Defaults().addModule(kotlinModule()).build()

    /** Blizzard API responses: no module discovery, so the Kotlin module does not change DTO defaults. */
    val blizzardApi: JsonMapper = JsonMapper.builder().build()
}

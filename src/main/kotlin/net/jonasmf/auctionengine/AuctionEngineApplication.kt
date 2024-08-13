package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableConfigurationProperties(BlizzardApiProperties::class)
@EnableScheduling
@SpringBootApplication
class AuctionEngineApplication

fun main(args: Array<String>) {
    runApplication<AuctionEngineApplication>(*args)
}

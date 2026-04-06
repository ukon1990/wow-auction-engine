package net.jonasmf.auctionengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AuctionEngineApplication

fun main(args: Array<String>) {
    runApplication<AuctionEngineApplication>(*args)
}

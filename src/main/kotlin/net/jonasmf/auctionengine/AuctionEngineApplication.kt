package net.jonasmf.auctionengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class AuctionEngineApplication

fun main(args: Array<String>) {
    runApplication<AuctionEngineApplication>(*args)
}

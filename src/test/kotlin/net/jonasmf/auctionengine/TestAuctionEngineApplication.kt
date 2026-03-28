package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.config.MariaDBTestcontainersConfig
import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<AuctionEngineApplication>().with(MariaDBTestcontainersConfig::class).run(*args)
}

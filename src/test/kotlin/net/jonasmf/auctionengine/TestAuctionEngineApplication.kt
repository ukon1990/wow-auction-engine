package net.jonasmf.auctionengine

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<AuctionEngineApplication>().with(TestcontainersConfiguration::class).run(*args)
}

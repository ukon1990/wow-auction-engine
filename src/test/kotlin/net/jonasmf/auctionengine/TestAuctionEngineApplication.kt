package net.jonasmf.auctionengine

import net.jonasmf.auctionengine.config.StubAuthWebClientConfig
import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    SharedTestContainers.configureSystemProperties()
    fromApplication<AuctionEngineApplication>().with(StubAuthWebClientConfig::class).run(*args)
}

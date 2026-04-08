package net.jonasmf.auctionengine.integration.blizzard

import reactor.core.publisher.Mono

class ProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    // profession index
    fun getAll() {}

    fun getById(id: Int): Mono<Any> {}
}

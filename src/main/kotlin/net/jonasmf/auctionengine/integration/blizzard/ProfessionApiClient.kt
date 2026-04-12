package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.profession.ProfessionIndexDTO
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

const val PROFESSION_INDEX_PATH = "/data/wow/profession/index"

@Component
class ProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    // profession index
    fun getAll(): ProfessionIndexDTO {
        // TODO: Cleanup, not sure if there is a point passing in region. blizzard api support has this info
        val region = Region.NorthAmerica
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = PROFESSION_INDEX_PATH,
                namespace = blizzardApiSupport.dynamicNamespaceForRegion(region).value,
            )
        return blizzardApiSupport
            .webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono<ProfessionIndexDTO>()
            .block()!!
    }

    // fun getById(id: Int): Mono<Any> {}
}

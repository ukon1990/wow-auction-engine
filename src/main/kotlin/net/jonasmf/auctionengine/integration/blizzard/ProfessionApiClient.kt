package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO
import net.jonasmf.auctionengine.dto.profession.ProfessionIndexDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

const val PROFESSION_BASE_PATH = "/data/wow/profession/index"
const val PROFESSION_INDEX_PATH = "$PROFESSION_BASE_PATH/index"

@Component
class ProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    /**
     * This call is slow, as it will combine multiple calls sequentially to get throttled
     * while we are downloading AH data etc.
     *
     * it is ok for this call, as a user will NEVER call this directly, but it is only meant to
     * fetch data, process and store in the database.
     */
    fun getAll(): List<Profession> {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                path = PROFESSION_INDEX_PATH,
                namespace = blizzardApiSupport.dynamicNamespaceForRegion().value,
            )
        val index =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<ProfessionIndexDTO>()
                .block()!!
        return getProfessionsFromIndex(index)
    }

    private fun getProfessionsFromIndex(index: ProfessionIndexDTO): List<Profession> {
        val professions = mutableListOf<Profession>()
        index.professions.forEach {
            val uri =
                blizzardApiSupport.buildRegionalUri(
                    path = PROFESSION_BASE_PATH + "/${it.id}",
                    namespace = blizzardApiSupport.dynamicNamespaceForRegion().value,
                )
            val profession =
                blizzardApiSupport
                    .webClient()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono<ProfessionDTO>()
                    .block()!!
            val domain = profession.toDomain()

            professions.add(domain)
        }
        return professions
    }

    fun getSkillTier(professionId: Int, skillTierId: Int): List<SkillTier> {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                path = "$PROFESSION_BASE_PATH/$professionId/skill-tier/$skillTierId",
                namespace = blizzardApiSupport.dynamicNamespaceForRegion().value,
            )
        val skillTier =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<SkillTierDTO>()
                .block()!!
        return listOf(skillTier.toDomain())
    }}

    // fun getById(id: Int): Mono<Any> {}
}

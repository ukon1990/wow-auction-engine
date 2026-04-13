package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO
import net.jonasmf.auctionengine.dto.profession.ProfessionIndexDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

const val PROFESSION_BASE_PATH = "/data/wow/profession"
const val PROFESSION_INDEX_PATH = "$PROFESSION_BASE_PATH/index"

@Component
class ProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
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

    private fun getProfessionsFromIndex(index: ProfessionIndexDTO): List<Profession> =
        index.professions.map { getById(it.id) }

    fun getSkillTier(
        professionId: Int,
        skillTierId: Int,
    ): SkillTier {
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
        return skillTier.toDomain()
    }

    fun getById(id: Int): Profession {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                path = "$PROFESSION_BASE_PATH/$id",
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
        val skillTiers = profession.skillTiers.map { tier -> getSkillTier(profession.id, tier.id) }
        return profession.toDomain(skillTiers)
    }
}

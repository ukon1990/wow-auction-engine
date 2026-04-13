package net.jonasmf.auctionengine.dto.profession

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.MediaDTO
import net.jonasmf.auctionengine.dto.ReferenceDTO

enum class ProfessionType {
    PRIMARY,
    SECONDARY,
}

data class ProfessionTypeDTO(
    val type: ProfessionType,
    val name: LocaleDTO,
)

data class ProfessionDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO,
    val media: MediaDTO,
    @JsonProperty("skill_tiers")
    val skillTiers: List<ReferenceDTO>,
)

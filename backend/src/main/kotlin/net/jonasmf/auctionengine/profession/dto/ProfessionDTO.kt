package net.jonasmf.auctionengine.profession.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.MediaDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO

enum class ProfessionType {
    PRIMARY,
    SECONDARY,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfessionTypeDTO(
    val type: ProfessionType,
    val name: LocaleDTO,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfessionDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO,
    val media: MediaDTO,
    @JsonProperty("skill_tiers")
    val skillTiers: List<ReferenceDTO> = emptyList(),
)

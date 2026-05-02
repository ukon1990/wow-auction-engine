package net.jonasmf.auctionengine.dto.profession

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.dto.ReferenceDTO

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryDTO(
    val name: LocaleDTO,
    val recipes: List<ReferenceDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SkillTierDTO(
    @JsonProperty("_links")
    val links: Links,
    val id: Int,
    val name: LocaleDTO,
    @JsonProperty("minimum_skill_level")
    val minimumSkillLevel: Int,
    @JsonProperty("maximum_skill_level")
    val maximumSkillLevel: Int,
    val categories: List<CategoryDTO> = emptyList(),
)

package net.jonasmf.auctionengine.profession.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.ReferenceDTO

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
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val categories: List<CategoryDTO> = emptyList(),
)

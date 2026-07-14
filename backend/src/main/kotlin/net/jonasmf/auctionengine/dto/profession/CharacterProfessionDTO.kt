package net.jonasmf.auctionengine.dto.profession

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class CharacterProfessionsDTO(
    val primaries: List<CharacterProfessionDTO> = emptyList(),
    val secondaries: List<CharacterProfessionDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CharacterProfessionDTO(
    val profession: CharacterProfessionReferenceDTO,
    val tiers: List<CharacterProfessionTierDTO> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CharacterProfessionReferenceDTO(
    val id: Int,
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CharacterProfessionTierDTO(
    @JsonProperty("skill_points")
    val skillPoints: Int,
    @JsonProperty("max_skill_points")
    val maxSkillPoints: Int,
    val tier: CharacterProfessionReferenceDTO,
    @JsonProperty("known_recipes")
    val knownRecipes: List<CharacterProfessionReferenceDTO> = emptyList(),
)

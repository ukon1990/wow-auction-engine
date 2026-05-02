package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO

data class ProfessionCategory(
    val name: LocaleDTO,
    val recipes: List<Recipe> = emptyList(),
)

data class SkillTier(
    val id: Int,
    val name: LocaleDTO,
    val minimumSkillLevel: Int,
    val maximumSkillLevel: Int,
    val categories: List<ProfessionCategory> = emptyList(),
)

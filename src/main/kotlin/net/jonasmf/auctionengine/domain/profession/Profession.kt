package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO

data class Profession(
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO,
    val mediaUrl: String,
    val skillTiers: List<SkillTier> = emptyList(),
)

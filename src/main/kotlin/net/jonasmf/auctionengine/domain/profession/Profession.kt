package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO
import java.time.Instant

data class Profession(
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO,
    val mediaUrl: String,
    val lastModified: Instant? = null,
    val skillTiers: List<SkillTier> = emptyList(),
)

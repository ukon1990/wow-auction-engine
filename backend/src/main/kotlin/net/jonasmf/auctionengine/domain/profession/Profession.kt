package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.locale.LocaleDTO
import java.time.Instant

data class Profession(
    val id: Int,
    val name: LocaleDTO,
    val description: LocaleDTO,
    val mediaUrl: String,
    val mediaSourceUrl: String? = null,
    val lastModified: Instant? = null,
    val skillTiers: List<SkillTier> = emptyList(),
)

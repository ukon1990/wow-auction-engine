package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO

fun ProfessionDTO.toDomain(
    skillTiers: List<SkillTier> = emptyList(),
    lastModified: java.time.Instant? = null,
) = Profession(
    id = id,
    name = name,
    description = description,
    mediaUrl = media.key.href,
    lastModified = lastModified,
    skillTiers = skillTiers,
)

fun Profession.toDBO() =
    ProfessionDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.PROFESSION, localeSourceKey(id), "name"),
        description = description.toDBO(LocaleSourceType.PROFESSION, localeSourceKey(id), "description"),
        mediaUrl = mediaUrl,
        lastModified = lastModified,
        skillTiers = skillTiers.map { it.toDBO() }.toMutableList(),
    )

fun ProfessionDBO.toDomain() =
    Profession(
        id = id,
        name = name.toDTO(),
        description = description.toDTO(),
        mediaUrl = mediaUrl,
        lastModified = lastModified,
        skillTiers = skillTiers.map { it.toDomain() },
    )

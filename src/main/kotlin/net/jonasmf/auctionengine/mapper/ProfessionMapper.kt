package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO

fun ProfessionDTO.toDomain(skillTiers: List<SkillTier> = emptyList()) =
    Profession(
        id = id,
        name = name,
        description = description,
        mediaUrl = media.key.href,
        skillTiers = skillTiers,
    )

fun Profession.toDBO() =
    ProfessionDBO(
        id = id,
        name = name.toDBO(),
        description = description.toDBO(),
        mediaUrl = mediaUrl,
        skillTiers = skillTiers.map { it.toDBO() }.toMutableList(),
    )

fun ProfessionDBO.toDomain() =
    Profession(
        id = id,
        name = name.toDTO(),
        description = description.toDTO(),
        mediaUrl = mediaUrl,
        skillTiers = skillTiers.map { it.toDomain() },
    )

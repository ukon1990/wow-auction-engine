package net.jonasmf.auctionengine.profession

import net.jonasmf.auctionengine.locale.toDBO
import net.jonasmf.auctionengine.item.toDBO
import net.jonasmf.auctionengine.auction.snapshot.toDBO
import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.locale.toDTO
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionDBO
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.profession.dto.ProfessionDTO

fun ProfessionDTO.toDomain(
    skillTiers: List<SkillTier> = emptyList(),
    lastModified: java.time.Instant? = null,
) = Profession(
    id = id,
    name = name,
    description = description,
    mediaUrl = media.key.href,
    mediaSourceUrl = media.key.href,
    lastModified = lastModified,
    skillTiers = skillTiers,
)

fun Profession.toDBO() =
    ProfessionDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.PROFESSION, localeSourceKey(id), "name"),
        description = description.toDBO(LocaleSourceType.PROFESSION, localeSourceKey(id), "description"),
        mediaUrl = mediaUrl,
        mediaSourceUrl = mediaSourceUrl,
        lastModified = lastModified,
        skillTiers = skillTiers.map { it.toDBO() }.toMutableList(),
    )

fun ProfessionDBO.toDomain() =
    Profession(
        id = id,
        name = name.toDTO(),
        description = description.toDTO(),
        mediaUrl = mediaUrl,
        mediaSourceUrl = mediaSourceUrl,
        lastModified = lastModified,
        skillTiers = skillTiers.map { it.toDomain() },
    )

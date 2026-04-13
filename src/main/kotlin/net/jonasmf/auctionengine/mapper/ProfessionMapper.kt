package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO

fun ProfessionDTO.toDomain() =
    Profession(
        id = id,
        name = name,
        description = description,
        // Not mapping this, as at this stage it's not available.
        skillTiers = mutableListOf(),
        mediaUrl = media.key.href,
    )

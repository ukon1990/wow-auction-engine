package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.domain.profession.Category
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO

fun Category.toDomain(): Category =
    Category(
        name = name,
        // recipes = recipes.mapValues { it. },
    )

fun SkillTierDTO.toDomain(): SkillTier =
    SkillTier(
        id = id,
        name = name,
        minimumSkillLevel = minimumSkillLevel,
        maximumSkillLevel = maximumSkillLevel,
        categories = categories.map { it.toDomain() },
    )

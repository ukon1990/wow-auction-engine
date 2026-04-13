package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.domain.profession.Category
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.CategoryDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO

fun CategoryDTO.toDomain(): Category =
    Category(
        name = name,
        /*
         *  We do not get a complete object from the profession API, just the ID and name,
         *  and I don't feel like creating two types for recipe at this stage.
         */
        recipes = recipes.associate { it.id to null },
    )

fun SkillTierDTO.toDomain(): SkillTier =
    SkillTier(
        id = id,
        name = name,
        minimumSkillLevel = minimumSkillLevel,
        maximumSkillLevel = maximumSkillLevel,
        categories = categories.map { it.toDomain() },
    )

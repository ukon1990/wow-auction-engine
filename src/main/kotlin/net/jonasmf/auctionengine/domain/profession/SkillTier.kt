package net.jonasmf.auctionengine.domain.profession

import net.jonasmf.auctionengine.dto.LocaleDTO

class Category(
    val name: LocaleDTO,
    val recipes: Map<Int, Recipe?>,
)

class SkillTier(
    val id: Int,
    val name: LocaleDTO,
    val minimumSkillLevel: Int,
    val maximumSkillLevel: Int,
    val categories: List<Category>,
)

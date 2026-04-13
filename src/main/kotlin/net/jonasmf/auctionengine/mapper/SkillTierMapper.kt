package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleSourceType
import net.jonasmf.auctionengine.dbo.rds.localeSourceKey
import net.jonasmf.auctionengine.dbo.rds.profession.ProfessionCategoryDBO
import net.jonasmf.auctionengine.dbo.rds.profession.SkillTierDBO
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.ReferenceDTO
import net.jonasmf.auctionengine.dto.profession.CategoryDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO

fun CategoryDTO.toDomain(): ProfessionCategory =
    ProfessionCategory(
        name = name,
        recipes = recipes.map { it.toRecipeStub() },
    )

fun SkillTierDTO.toDomain(): SkillTier =
    SkillTier(
        id = id,
        name = name,
        minimumSkillLevel = minimumSkillLevel,
        maximumSkillLevel = maximumSkillLevel,
        categories = categories.map { it.toDomain() },
    )

fun ProfessionCategory.toDBO(
    skillTierId: Int,
    categoryIndex: Int,
) =
    ProfessionCategoryDBO(
        name = name.toDBO(LocaleSourceType.PROFESSION_CATEGORY, localeSourceKey(skillTierId, categoryIndex), "name"),
        recipes = recipes.map { it.toDBO() }.toMutableList(),
    )

fun ProfessionCategoryDBO.toDomain() =
    ProfessionCategory(
        name = name.toDTO(),
        recipes = recipes.map { it.toDomain() },
    )

fun SkillTier.toDBO() =
    SkillTierDBO(
        id = id,
        name = name.toDBO(LocaleSourceType.SKILL_TIER, localeSourceKey(id), "name"),
        minimumSkillLevel = minimumSkillLevel,
        maximumSkillLevel = maximumSkillLevel,
        categories = categories.mapIndexed { index, category -> category.toDBO(id, index) }.toMutableList(),
    )

fun SkillTierDBO.toDomain() =
    SkillTier(
        id = id,
        name = name.toDTO(),
        minimumSkillLevel = minimumSkillLevel,
        maximumSkillLevel = maximumSkillLevel,
        categories = categories.map { it.toDomain() },
    )

fun ReferenceDTO.toProfessionCategoryRecipeStub() = toRecipeStub()

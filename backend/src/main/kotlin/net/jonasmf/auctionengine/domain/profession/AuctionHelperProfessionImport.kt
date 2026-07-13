package net.jonasmf.auctionengine.domain.profession

import java.time.Instant

data class AuctionHelperProfessionImport(
    val addonVersion: String?,
    val characters: List<AuctionHelperCharacter>,
    val contentHash: String,
    val importedAt: Instant,
    val diagnostics: List<AuctionHelperImportDiagnostic>,
)

data class AuctionHelperCharacter(
    val name: String,
    val realm: String,
    val guid: String?,
    val schemaVersion: Int?,
    val build: AuctionHelperBuild?,
    val professions: List<AuctionHelperCharacterProfession>,
)

data class AuctionHelperBuild(
    val version: String?,
    val build: String?,
    val interfaceVersion: Int?,
)

data class AuctionHelperCharacterProfession(
    val skillLineId: Int,
    val currentLevelName: String?,
    val skillLevel: Int?,
    val recipes: List<AuctionHelperRecipe>,
)

data class AuctionHelperRecipe(
    val recipeId: Int,
    val name: String?,
    val categoryId: Int?,
    val learned: Boolean?,
    val qualityVariantItemIds: List<Int>,
)

data class AuctionHelperImportDiagnostic(
    val code: AuctionHelperImportDiagnosticCode,
    val detail: String,
)

enum class AuctionHelperImportDiagnosticCode {
    TALENT_DATA_MISSING,
    UNSUPPORTED_TALENT_EXPORT,
    INPUT_LIMIT_EXCEEDED,
    MALFORMED_INPUT,
}

/**
 * Contract boundary for a future, separately versioned `professions_talents` export.
 * AuctionHelper_Professions.lua intentionally does not supply tree/config/rank fields, so this
 * model does not guess their names or IDs. Add a concrete version only with a representative export.
 */
data class ProfessionTalentExport(
    val formatVersion: Int,
    val sourceVersion: String?,
    val trees: List<ProfessionTalentTree>,
)

data class ProfessionTalentTree(
    val expansionId: Int,
    val professionId: Int,
    val externalTreeId: Int,
    val tabs: List<ProfessionTalentTab>,
)

data class ProfessionTalentTab(
    val externalTabId: Int,
    val name: String,
    val displayOrder: Int,
)

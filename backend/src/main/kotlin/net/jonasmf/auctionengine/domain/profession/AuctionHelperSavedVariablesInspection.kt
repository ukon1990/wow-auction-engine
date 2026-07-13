package net.jonasmf.auctionengine.domain.profession

import java.time.Instant

/**
 * Inspection only: no SavedVariables upload changes shared talent tree definitions or profiles.
 */
data class AuctionHelperSavedVariablesInspection(
    val imported: Boolean = false,
    val inspectedAt: Instant,
    val sources: List<AuctionHelperSavedVariablesSource>,
    val charactersFound: Int,
    val professionsFound: Int,
    val recipesFound: Int,
    val talentExport: AuctionHelperTalentExportInspection?,
    val diagnostics: List<AuctionHelperImportDiagnostic>,
)

data class AuctionHelperSavedVariablesSource(
    val fileName: String,
    val status: AuctionHelperSavedVariablesSourceStatus,
    val contentHash: String?,
)

enum class AuctionHelperSavedVariablesSourceStatus {
    FOUND,
    MISSING,
    INVALID,
}

data class AuctionHelperTalentExportInspection(
    val format: String,
    val scope: String?,
    val module: String?,
    val characterKey: String?,
    val professionIdentifier: String?,
    val decodedBytes: Int,
    val validTalentScope: Boolean,
)

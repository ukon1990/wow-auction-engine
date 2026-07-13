package net.jonasmf.auctionengine.domain.profession

import java.time.Instant

/**
 * A diagnostic result for an AuctionHelper SavedVariables upload. This intentionally does not
 * report success unless a future, explicitly versioned Lua talent export is supported.
 */
data class AuctionHelperTalentTreeLuaImportResult(
    val imported: Boolean = false,
    val contentHash: String,
    val importedAt: Instant,
    val charactersFound: Int,
    val professionsFound: Int,
    val recipesFound: Int,
    val diagnostics: List<AuctionHelperImportDiagnostic>,
)

package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.domain.profession.AuctionHelperTalentTreeLuaImportResult
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnostic
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnosticCode
import net.jonasmf.auctionengine.service.MAX_AUCTION_HELPER_IMPORT_BYTES
import net.jonasmf.auctionengine.service.AuctionHelperProfessionImportParser
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ProfessionTalentTreeLuaImportService(
    private val parser: AuctionHelperProfessionImportParser,
) {
    /**
     * Parses the bounded SavedVariables format without changing shared tree definitions.
     *
     * AuctionHelper_Professions.lua does not contain the tree/node/rank/configuration data
     * required by the JSON import contract, so it is always returned as a diagnostic result.
     */
    fun inspect(input: ByteArray): AuctionHelperTalentTreeLuaImportResult {
        val import = parser.missingTalentDataDiagnostic(input) ?: parser.parse(input)
        return AuctionHelperTalentTreeLuaImportResult(
            contentHash = import.contentHash,
            importedAt = import.importedAt,
            charactersFound = import.characters.size,
            professionsFound = import.characters.sumOf { it.professions.size },
            recipesFound = import.characters.sumOf { character -> character.professions.sumOf { it.recipes.size } },
            diagnostics = import.diagnostics,
        )
    }

    fun oversizedResult(): AuctionHelperTalentTreeLuaImportResult =
        AuctionHelperTalentTreeLuaImportResult(
            contentHash = "",
            importedAt = Instant.now(),
            charactersFound = 0,
            professionsFound = 0,
            recipesFound = 0,
            diagnostics =
                listOf(
                    AuctionHelperImportDiagnostic(
                        AuctionHelperImportDiagnosticCode.INPUT_LIMIT_EXCEEDED,
                        "SavedVariables input exceeds ${MAX_AUCTION_HELPER_IMPORT_BYTES / 1024 / 1024} MiB",
                    ),
                ),
        )
}

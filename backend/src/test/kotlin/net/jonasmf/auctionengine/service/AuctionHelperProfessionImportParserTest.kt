package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnosticCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuctionHelperProfessionImportParserTest {
    private val parser =
        AuctionHelperProfessionImportParser(
            Clock.fixed(Instant.parse("2026-07-12T10:15:30Z"), ZoneOffset.UTC),
        )

    @Test
    fun `extracts character profession recipe and source metadata`() {
        val result = parser.parse(fixture.toByteArray())

        assertThat(result.addonVersion).isEqualTo("2.0.0")
        assertThat(result.importedAt).isEqualTo(Instant.parse("2026-07-12T10:15:30Z"))
        assertThat(result.contentHash).hasSize(64)
        val character = result.characters.single()
        assertThat(character.name).isEqualTo("Stinsön")
        assertThat(character.realm).isEqualTo("Draenor")
        assertThat(character.guid).isEqualTo("Player-1403-0A6FF102")
        assertThat(character.schemaVersion).isEqualTo(9)
        assertThat(character.build?.version).isEqualTo("12.0.7")
        val profession = character.professions.single()
        assertThat(profession.skillLineId).isEqualTo(182)
        assertThat(profession.skillLevel).isEqualTo(100)
        val recipe = profession.recipes.single()
        assertThat(recipe.recipeId).isEqualTo(435823)
        assertThat(recipe.name).isEqualTo("Irradiated Blessing Blossom")
        assertThat(recipe.categoryId).isEqualTo(2004)
        assertThat(recipe.learned).isTrue()
        assertThat(recipe.qualityVariantItemIds).containsExactly(245001, 245002)
    }

    @Test
    fun `reports that SavedVariables input does not include talent allocations`() {
        val result = parser.parse(fixture.toByteArray())

        val diagnostic = result.diagnostics.single()
        assertThat(diagnostic.code).isEqualTo(AuctionHelperImportDiagnosticCode.TALENT_DATA_MISSING)
        assertThat(diagnostic.detail).contains("no specialization tree")
    }

    @Test
    fun `reports malformed input without exposing parser failure`() {
        val result = parser.parse("AuctionHelperProfessionsDB = {".toByteArray())

        assertThat(result.characters).isEmpty()
        assertThat(result.diagnostics.single().code).isEqualTo(AuctionHelperImportDiagnosticCode.MALFORMED_INPUT)
    }

    @Test
    fun `bounds oversized SavedVariables input at 64 MiB`() {
        val result = parser.parse(ByteArray(64 * 1024 * 1024 + 1))

        assertThat(result.characters).isEmpty()
        assertThat(result.diagnostics.single().code).isEqualTo(AuctionHelperImportDiagnosticCode.INPUT_LIMIT_EXCEEDED)
        assertThat(result.diagnostics.single().detail).contains("64 MiB")
    }

    @Test
    fun `bounds total table entries across nested tables`() {
        val result = parser.parse(nestedTableEntryLimitFixture().toByteArray())

        assertThat(result.characters).isEmpty()
        assertThat(result.diagnostics.single().code).isEqualTo(AuctionHelperImportDiagnosticCode.MALFORMED_INPUT)
        assertThat(result.diagnostics.single().detail).contains("Lua table entry limit exceeded")
    }

    @Test
    fun `reports missing talent data without parsing a recipe only SavedVariables tree`() {
        val result = parser.missingTalentDataDiagnostic("AuctionHelperProfessionsDB = { [\"recipes\"] = {} }".toByteArray())

        assertThat(result?.characters).isEmpty()
        assertThat(result?.diagnostics?.single()?.code).isEqualTo(AuctionHelperImportDiagnosticCode.TALENT_DATA_MISSING)
    }

    private fun nestedTableEntryLimitFixture(): String =
        buildString {
            append("AuctionHelperProfessionsDB = { [\"first\"] = {")
            repeat(50_000) { index -> append("[").append(index).append("] = true,") }
            append("}, [\"second\"] = {")
            repeat(50_000) { index -> append("[").append(index).append("] = true,") }
            append("} }")
        }

    private val fixture =
        """
        AuctionHelperProfessionsDB = {
          ["addonVersion"] = "2.0.0",
          ["characters"] = {
            ["Stinsön-Draenor"] = {
              ["meta"] = {
                ["guid"] = "Player-1403-0A6FF102",
                ["realm"] = "Draenor",
                ["name"] = "Stinsön",
                ["build"] = { ["interfaceVersion"] = 120007, ["version"] = "12.0.7", ["build"] = "68453" },
              },
              ["schemaVersion"] = 9,
              ["professions"] = {
                ["182"] = {
                  ["currentLevelName"] = "Midnight Herbalism",
                  ["skillLevel"] = 100,
                  ["skillLineID"] = 182,
                  ["recipes"] = {
                    ["435823"] = {
                      ["info"] = { ["recipeID"] = 435823, ["name"] = "Irradiated Blessing Blossom", ["categoryID"] = 2004, ["learned"] = true },
                      ["outputs"] = { ["qualityVariants"] = { ["1"] = { ["itemID"] = 245001 }, ["2"] = { ["itemID"] = 245002 } } },
                    },
                  },
                },
              },
            },
          },
        }
        """.trimIndent()
}

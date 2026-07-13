package net.jonasmf.auctionengine.service.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnosticCode
import net.jonasmf.auctionengine.domain.profession.AuctionHelperSavedVariablesSourceStatus
import net.jonasmf.auctionengine.service.AuctionHelperProfessionImportParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class AuctionHelperSavedVariablesInspectionServiceTest {
    private val service = AuctionHelperSavedVariablesInspectionService(
        AuctionHelperProfessionImportParser(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    )

    @Test
    fun `inspects both recognized SavedVariables artifacts without mutation`() {
        val result = service.inspect(
            listOf(
                file("AuctionHelper.lua", talentExportLua(scope = "profession_talents")),
                file("AuctionHelper_Professions.lua", "AuctionHelperProfessionsDB = { [\"characters\"] = {} }"),
            ),
        )

        assertThat(result.imported).isFalse()
        assertThat(result.sources.map { it.status }).containsOnly(AuctionHelperSavedVariablesSourceStatus.FOUND)
        assertThat(result.talentExport?.validTalentScope).isTrue()
        assertThat(result.talentExport?.decodedBytes).isGreaterThan(0)
        assertThat(result.diagnostics.map { it.code }).contains(AuctionHelperImportDiagnosticCode.TALENT_DATA_MISSING)
    }

    @Test
    fun `reports an unsupported export scope after bounded decoding`() {
        val result = service.inspect(listOf(file("AuctionHelper.lua", talentExportLua(scope = "profession"))))

        assertThat(result.sources[0].status).isEqualTo(AuctionHelperSavedVariablesSourceStatus.FOUND)
        assertThat(result.sources[1].status).isEqualTo(AuctionHelperSavedVariablesSourceStatus.MISSING)
        assertThat(result.talentExport?.validTalentScope).isFalse()
        assertThat(result.diagnostics.single().code).isEqualTo(AuctionHelperImportDiagnosticCode.UNSUPPORTED_TALENT_EXPORT)
    }

    @Test
    fun `reads fields only from the AuctionHelperLastExport table`() {
        val result =
            service.inspect(
                listOf(
                    file(
                        "AuctionHelper.lua",
                        talentExportLua(scope = "profession_talents") +
                            "\nOtherSavedVariable = { [\"scope\"] = \"profession\", [\"payload\"] = \"not-an-export\" }",
                    ),
                ),
            )

        assertThat(result.talentExport?.scope).isEqualTo("profession_talents")
        assertThat(result.talentExport?.validTalentScope).isTrue()
    }

    @Test
    fun `rejects a persisted scope that disagrees with decoded payload metadata`() {
        val result = service.inspect(listOf(file("AuctionHelper.lua", talentExportLua(scope = "profession_talents").replaceFirst("[\"scope\"] = \"profession_talents\"", "[\"scope\"] = \"profession\""))))

        assertThat(result.talentExport).isNull()
        assertThat(result.diagnostics.single().code).isEqualTo(AuctionHelperImportDiagnosticCode.MALFORMED_INPUT)
    }

    @Test
    fun `rejects an unrelated file name`() {
        assertThatThrownBy { service.inspect(listOf(file("Other.lua", "x"))) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Only AuctionHelper.lua and AuctionHelper_Professions.lua are accepted")
    }

    private fun file(name: String, content: String) = MockMultipartFile("files", name, "text/plain", content.toByteArray())

    private fun talentExportLua(scope: String): String {
        val encoded = ObjectMapper(CBORFactory()).writeValueAsBytes(mapOf("meta" to mapOf("scope" to scope)))
        val payload = Base64.getEncoder().encodeToString(deflate(encoded))
        return """
            AuctionHelperLastExport = {
              ["scope"] = "$scope",
              ["format"] = "AHCBOR1",
              ["module"] = "profession",
              ["payload"] = "AHCBOR1:$payload",
            }
        """.trimIndent()
    }

    private fun deflate(input: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output, Deflater(Deflater.DEFAULT_COMPRESSION, true)).use { it.write(input) }
            output.toByteArray()
        }
}

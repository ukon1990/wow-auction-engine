package net.jonasmf.auctionengine.service.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnostic
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnosticCode
import net.jonasmf.auctionengine.domain.profession.AuctionHelperSavedVariablesInspection
import net.jonasmf.auctionengine.domain.profession.AuctionHelperSavedVariablesSource
import net.jonasmf.auctionengine.domain.profession.AuctionHelperSavedVariablesSourceStatus
import net.jonasmf.auctionengine.domain.profession.AuctionHelperTalentExportInspection
import net.jonasmf.auctionengine.service.AuctionHelperProfessionImportParser
import net.jonasmf.auctionengine.service.MAX_AUCTION_HELPER_IMPORT_BYTES
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.Base64
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

private const val AUCTION_HELPER_FILE = "AuctionHelper.lua"
private const val PROFESSIONS_FILE = "AuctionHelper_Professions.lua"
private const val MAX_TOTAL_IMPORT_BYTES = 128 * 1024 * 1024
private const val MAX_DECOMPRESSED_EXPORT_BYTES = 16 * 1024 * 1024
private val ALLOWED_FILES = setOf(AUCTION_HELPER_FILE, PROFESSIONS_FILE)
private val TALENT_SCOPES = setOf("profession_talents", "professions_talents")

@Service
class AuctionHelperSavedVariablesInspectionService(
    private val professionParser: AuctionHelperProfessionImportParser,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val cborMapper = ObjectMapper(CBORFactory())

    fun inspect(files: List<MultipartFile>): AuctionHelperSavedVariablesInspection {
        validateFiles(files)
        val inputByName = files.associate { it.requiredOriginalName() to it.bytes }
        val professions = inputByName[PROFESSIONS_FILE]?.let(professionParser::parse)
        val export = inputByName[AUCTION_HELPER_FILE]?.let(::inspectTalentExport)
        val diagnostics = buildList {
            professions?.diagnostics?.let(::addAll)
            export?.diagnostics.orEmpty().forEach(::add)
        }
        return AuctionHelperSavedVariablesInspection(
            inspectedAt = clock.instant(),
            sources = listOf(
                inputByName.source(AUCTION_HELPER_FILE),
                inputByName.source(PROFESSIONS_FILE),
            ),
            charactersFound = professions?.characters?.size ?: 0,
            professionsFound = professions?.characters?.sumOf { it.professions.size } ?: 0,
            recipesFound = professions?.characters?.sumOf { character -> character.professions.sumOf { it.recipes.size } } ?: 0,
            talentExport = export?.inspection,
            diagnostics = diagnostics,
        )
    }

    private fun validateFiles(files: List<MultipartFile>) {
        if (files.isEmpty()) badRequest("Select AuctionHelper.lua and/or AuctionHelper_Professions.lua")
        if (files.size > ALLOWED_FILES.size) badRequest("Only $AUCTION_HELPER_FILE and $PROFESSIONS_FILE are accepted")
        val names = files.map { it.requiredOriginalName() }
        if (!names.all(ALLOWED_FILES::contains)) badRequest("Only $AUCTION_HELPER_FILE and $PROFESSIONS_FILE are accepted")
        if (names.toSet().size != names.size) badRequest("Each SavedVariables file may be provided once")
        if (files.any { it.size > MAX_AUCTION_HELPER_IMPORT_BYTES }) badRequest("Each SavedVariables file must be at most 64 MiB")
        if (files.sumOf { it.size } > MAX_TOTAL_IMPORT_BYTES) badRequest("SavedVariables folder upload must be at most 128 MiB")
    }

    private fun inspectTalentExport(input: ByteArray): TalentExportResult = runCatching {
        val fields = extractExportFields(input)
        val payload = fields["payload"] ?: error("AuctionHelperLastExport.payload is missing")
        require(payload.startsWith("AHCBOR1:")) { "AuctionHelperLastExport.payload must start with AHCBOR1:" }
        val compressed = Base64.getDecoder().decode(payload.removePrefix("AHCBOR1:"))
        val decoded = inflateBounded(compressed)
        val decodedRoot = cborMapper.readTree(decoded).also { require(it.isObject) { "Decoded AHCBOR1 payload must be a CBOR object" } }
        val meta = decodedRoot.path("meta").also { require(it.isObject) { "Decoded AHCBOR1 payload meta is missing" } }
        val scope = meta.path("scope").takeIf { it.isTextual }?.asText()
        val persistedScope = fields["scope"]
        require(persistedScope == null || persistedScope == scope) {
            "AuctionHelperLastExport scope does not match decoded payload scope"
        }
        TalentExportResult(
            AuctionHelperTalentExportInspection(
                format = meta.path("format").takeIf { it.isTextual }?.asText() ?: fields["format"] ?: "AHCBOR1",
                scope = scope,
                module = meta.path("module").takeIf { it.isTextual }?.asText() ?: fields["module"],
                characterKey = meta.path("characterKey").takeIf { it.isTextual }?.asText() ?: fields["characterKey"],
                professionIdentifier = meta.path("professionIdentifier").takeIf { it.isTextual }?.asText() ?: fields["professionIdentifier"],
                decodedBytes = decoded.size,
                validTalentScope = scope in TALENT_SCOPES,
            ),
            if (scope in TALENT_SCOPES) emptyList() else listOf(
                AuctionHelperImportDiagnostic(
                    AuctionHelperImportDiagnosticCode.UNSUPPORTED_TALENT_EXPORT,
                    "AuctionHelperLastExport scope must be profession_talents or professions_talents; found ${scope ?: "none"}",
                ),
            ),
        )
    }.getOrElse { exception ->
        TalentExportResult(
            inspection = null,
            diagnostics = listOf(AuctionHelperImportDiagnostic(AuctionHelperImportDiagnosticCode.MALFORMED_INPUT, exception.message ?: "Unable to decode AuctionHelperLastExport")),
        )
    }

    private fun extractExportFields(input: ByteArray): Map<String, String> {
        val content = input.decodeToString()
        val assignment = Regex("(?m)^AuctionHelperLastExport\\s*=").find(content)
            ?: error("AuctionHelperLastExport assignment is missing")
        val table = extractAssignedTable(content, assignment.range.last + 1)
        return EXPORT_FIELD.findAll(table).associate { it.groupValues[1] to it.groupValues[2] }
            .takeIf { it.isNotEmpty() } ?: error("AuctionHelperLastExport fields are malformed")
    }

    private fun inflateBounded(compressed: ByteArray): ByteArray =
        InflaterInputStream(ByteArrayInputStream(compressed), Inflater(true)).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_DECOMPRESSED_EXPORT_BYTES) error("Decoded AHCBOR1 payload exceeds 16 MiB")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }

    private fun Map<String, ByteArray>.source(fileName: String) = this[fileName]?.let {
        AuctionHelperSavedVariablesSource(fileName, AuctionHelperSavedVariablesSourceStatus.FOUND, it.sha256())
    } ?: AuctionHelperSavedVariablesSource(fileName, AuctionHelperSavedVariablesSourceStatus.MISSING, null)
}

private val EXPORT_FIELD = Regex("\\[\\\"([^\\\"]+)\\\"\\]\\s*=\\s*\\\"([^\\\"]*)\\\"")

private fun extractAssignedTable(
    content: String,
    assignmentEnd: Int,
): String {
    val start = content.indexOfFirstNonWhitespace(assignmentEnd)
    require(start >= 0 && content[start] == '{') { "AuctionHelperLastExport must be a Lua table" }

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until content.length) {
        val character = content[index]
        if (inString) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') inString = false
            continue
        }
        when (character) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return content.substring(start, index + 1)
            }
        }
    }
    error("AuctionHelperLastExport table is not closed")
}

private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
    for (index in startIndex until length) {
        if (!this[index].isWhitespace()) return index
    }
    return -1
}

private data class TalentExportResult(
    val inspection: AuctionHelperTalentExportInspection?,
    val diagnostics: List<AuctionHelperImportDiagnostic>,
)

private fun MultipartFile.requiredOriginalName(): String = originalFilename ?: badRequest("Uploaded SavedVariables file has no file name")

private fun ByteArray.sha256(): String = java.security.MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

private fun badRequest(detail: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, detail)

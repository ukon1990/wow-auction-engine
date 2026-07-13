package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.domain.profession.AuctionHelperBuild
import net.jonasmf.auctionengine.domain.profession.AuctionHelperCharacter
import net.jonasmf.auctionengine.domain.profession.AuctionHelperCharacterProfession
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnostic
import net.jonasmf.auctionengine.domain.profession.AuctionHelperImportDiagnosticCode
import net.jonasmf.auctionengine.domain.profession.AuctionHelperProfessionImport
import net.jonasmf.auctionengine.domain.profession.AuctionHelperRecipe
import net.jonasmf.auctionengine.domain.profession.ProfessionTalentExport
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

const val MAX_AUCTION_HELPER_IMPORT_BYTES = 64 * 1024 * 1024
private const val MAX_LUA_NESTING = 100
private const val MAX_LUA_TABLE_ENTRIES = 100_000

@Component
class AuctionHelperProfessionImportParser(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun parse(input: ByteArray): AuctionHelperProfessionImport {
        val importedAt = clock.instant()
        val contentHash = input.sha256()
        if (input.size > MAX_AUCTION_HELPER_IMPORT_BYTES) {
            return emptyImport(
                contentHash,
                importedAt,
                AuctionHelperImportDiagnostic(AuctionHelperImportDiagnosticCode.INPUT_LIMIT_EXCEEDED, "SavedVariables input exceeds 64 MiB"),
            )
        }

        return runCatching {
            val root = LuaSavedVariablesParser(input.toString(StandardCharsets.UTF_8)).parseAssignment("AuctionHelperProfessionsDB")
            root.toAuctionHelperImport(contentHash, importedAt)
        }.getOrElse { exception ->
            emptyImport(
                contentHash,
                importedAt,
                AuctionHelperImportDiagnostic(AuctionHelperImportDiagnosticCode.MALFORMED_INPUT, exception.message ?: "Unable to parse SavedVariables input"),
            )
        }
    }

    /**
     * Avoids materializing a large recipe-only SavedVariables tree when the file cannot possibly
     * contain the separately versioned professions_talents export required for tree imports.
     */
    fun missingTalentDataDiagnostic(input: ByteArray): AuctionHelperProfessionImport? {
        val content = input.toString(StandardCharsets.UTF_8)
        if (!content.trimStart().startsWith("AuctionHelperProfessionsDB") || content.contains("professions_talents")) {
            return null
        }

        return AuctionHelperProfessionImport(
            addonVersion = null,
            characters = emptyList(),
            contentHash = input.sha256(),
            importedAt = clock.instant(),
            diagnostics = listOf(talentDataMissingDiagnostic()),
        )
    }

    /**
     * Deliberately accepts no unversioned talent mapping. A real professions_talents export must
     * establish the format version and field semantics before allocations can be parsed safely.
     */
    fun parseTalentExport(input: ByteArray): Result<ProfessionTalentExport> =
        Result.failure(
            UnsupportedOperationException(
                "No supported professions_talents export version is registered; provide a representative versioned export",
            ),
        )

    private fun emptyImport(
        contentHash: String,
        importedAt: Instant,
        diagnostic: AuctionHelperImportDiagnostic,
    ) = AuctionHelperProfessionImport(null, emptyList(), contentHash, importedAt, listOf(diagnostic))
}

private fun LuaValue.toAuctionHelperImport(
    contentHash: String,
    importedAt: Instant,
): AuctionHelperProfessionImport {
    val root = asTable()
    val characters = root.table("characters").entries.mapNotNull { (_, characterValue) -> characterValue.toCharacter() }
    return AuctionHelperProfessionImport(
        addonVersion = root.string("addonVersion"),
        characters = characters,
        contentHash = contentHash,
        importedAt = importedAt,
        diagnostics = listOf(talentDataMissingDiagnostic()),
    )
}

private fun talentDataMissingDiagnostic() =
    AuctionHelperImportDiagnostic(
        AuctionHelperImportDiagnosticCode.TALENT_DATA_MISSING,
        "AuctionHelper_Professions.lua has character and recipe metadata but no specialization tree, node, entry, rank, or config data",
    )

private fun LuaValue.toCharacter(): AuctionHelperCharacter? {
    val table = asTable()
    val meta = table.table("meta")
    val name = meta.string("name") ?: return null
    val realm = meta.string("realm") ?: return null
    val build = meta.table("build")
    return AuctionHelperCharacter(
        name = name,
        realm = realm,
        guid = meta.string("guid"),
        schemaVersion = table.int("schemaVersion"),
        build = AuctionHelperBuild(build.string("version"), build.string("build"), build.int("interfaceVersion")),
        professions = table.table("professions").entries.mapNotNull { (_, value) -> value.toProfession() },
    )
}

private fun LuaValue.toProfession(): AuctionHelperCharacterProfession? {
    val table = asTable()
    val skillLineId = table.int("skillLineID") ?: return null
    return AuctionHelperCharacterProfession(
        skillLineId = skillLineId,
        currentLevelName = table.string("currentLevelName"),
        skillLevel = table.int("skillLevel"),
        recipes = table.table("recipes").entries.mapNotNull { (_, recipe) -> recipe.toRecipe() },
    )
}

private fun LuaValue.toRecipe(): AuctionHelperRecipe? {
    val info = asTable().table("info")
    val recipeId = info.int("recipeID") ?: return null
    val qualityVariants = asTable().table("outputs").table("qualityVariants").entries.mapNotNull { (_, value) ->
        value.asTable().int("itemID")
    }
    return AuctionHelperRecipe(recipeId, info.string("name"), info.int("categoryID"), info.boolean("learned"), qualityVariants)
}

private fun LuaTable.table(key: String): LuaTable = values[key]?.asTable() ?: LuaTable(emptyMap())

private fun LuaTable.string(key: String): String? = (values[key] as? LuaValue.StringValue)?.value

private fun LuaTable.int(key: String): Int? = (values[key] as? LuaValue.NumberValue)?.value?.toInt()

private fun LuaTable.boolean(key: String): Boolean? = (values[key] as? LuaValue.BooleanValue)?.value

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

private sealed interface LuaValue {
    data class StringValue(val value: String) : LuaValue

    data class NumberValue(val value: Long) : LuaValue

    data class BooleanValue(val value: Boolean) : LuaValue

    data class TableValue(val value: LuaTable) : LuaValue

    data object NilValue : LuaValue

    fun asTable(): LuaTable = (this as? TableValue)?.value ?: LuaTable(emptyMap())
}

private data class LuaTable(val values: Map<String, LuaValue>) {
    val entries: Set<Map.Entry<String, LuaValue>> get() = values.entries
}

private class LuaSavedVariablesParser(
    private val input: String,
) {
    private var position = 0
    private var totalTableEntries = 0

    fun parseAssignment(expectedName: String): LuaValue {
        skipWhitespace()
        require(readIdentifier() == expectedName) { "Expected $expectedName assignment" }
        skipWhitespace()
        require(read() == '=') { "Expected assignment" }
        skipWhitespace()
        val result = parseValue(0)
        skipWhitespace()
        require(position == input.length) { "Unexpected trailing input" }
        return result
    }

    private fun parseValue(depth: Int): LuaValue {
        require(depth <= MAX_LUA_NESTING) { "Lua nesting limit exceeded" }
        return when (peek()) {
            '{' -> LuaValue.TableValue(parseTable(depth + 1))
            '"' -> LuaValue.StringValue(readString())
            '-', in '0'..'9' -> LuaValue.NumberValue(readNumber())
            else ->
                when (val identifier = readIdentifier()) {
                    "true" -> LuaValue.BooleanValue(true)
                    "false" -> LuaValue.BooleanValue(false)
                    "nil" -> LuaValue.NilValue
                    else -> error("Unsupported Lua value $identifier")
                }
        }
    }

    private fun parseTable(depth: Int): LuaTable {
        require(read() == '{')
        val values = linkedMapOf<String, LuaValue>()
        var arrayIndex = 1
        skipWhitespace()
        while (peek() != '}') {
            require(totalTableEntries < MAX_LUA_TABLE_ENTRIES) { "Lua table entry limit exceeded" }
            totalTableEntries++
            val key =
                if (peek() == '[') {
                    read()
                    skipWhitespace()
                    val parsedKey = when (peek()) {
                        '"' -> readString()
                        else -> readNumber().toString()
                    }
                    skipWhitespace()
                    require(read() == ']') { "Expected closing table key bracket" }
                    skipWhitespace()
                    require(read() == '=') { "Expected table key assignment" }
                    parsedKey
                } else if (isIdentifierStart(peek()) && lookAheadHasAssignment()) {
                    readIdentifier().also {
                        skipWhitespace()
                        require(read() == '=')
                    }
                } else {
                    (arrayIndex++).toString()
                }
            skipWhitespace()
            values[key] = parseValue(depth)
            skipWhitespace()
            if (peek() == ',' || peek() == ';') {
                read()
                skipWhitespace()
            } else {
                require(peek() == '}') { "Expected table separator" }
            }
        }
        read()
        return LuaTable(values)
    }

    private fun lookAheadHasAssignment(): Boolean {
        var cursor = position
        while (cursor < input.length && (input[cursor].isLetterOrDigit() || input[cursor] == '_')) cursor++
        while (cursor < input.length && input[cursor].isWhitespace()) cursor++
        return cursor < input.length && input[cursor] == '='
    }

    private fun readString(): String {
        require(read() == '"')
        val result = StringBuilder()
        while (peek() != '"') {
            val character = read()
            if (character == '\\') {
                result.append(
                    when (val escaped = read()) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> escaped
                    },
                )
            } else {
                result.append(character)
            }
        }
        read()
        return result.toString()
    }

    private fun readNumber(): Long {
        val start = position
        if (peek() == '-') read()
        while (!atEnd() && peek().isDigit()) read()
        return input.substring(start, position).toLong()
    }

    private fun readIdentifier(): String {
        require(isIdentifierStart(peek())) { "Expected identifier" }
        val start = position
        read()
        while (!atEnd() && (peek().isLetterOrDigit() || peek() == '_')) read()
        return input.substring(start, position)
    }

    private fun skipWhitespace() {
        while (!atEnd() && input[position].isWhitespace()) position++
    }

    private fun isIdentifierStart(character: Char): Boolean = character.isLetter() || character == '_'

    private fun peek(): Char = if (atEnd()) error("Unexpected end of Lua input") else input[position]

    private fun read(): Char = peek().also { position++ }

    private fun atEnd(): Boolean = position >= input.length
}

package net.jonasmf.auctionengine.domain.profession

data class ParsedSkillTreeNodeEffect(
    val skillBonus: Int,
    val craftingCategory: String?,
)

object ProfessionSkillTreeNodeEffectParser {
    private val gainSkillPattern =
        Regex(
            """(?i)gain\s+\+(\d+)\s+skill\s+when\s+crafting\s+(.+)""",
        )
    private val plusSkillPattern =
        Regex(
            """(?i)\+(\d+)\s+(?:\p{Alpha}+\s+)?skill(?:\s+when\s+crafting\s+(.+))?""",
        )

    fun parseDescription(description: String?): ParsedSkillTreeNodeEffect? {
        val text = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        gainSkillPattern.find(text)?.let { match ->
            return ParsedSkillTreeNodeEffect(
                skillBonus = match.groupValues[1].toInt(),
                craftingCategory = match.groupValues[2].trim().trimEnd('.').ifBlank { null },
            )
        }
        plusSkillPattern.find(text)?.let { match ->
            return ParsedSkillTreeNodeEffect(
                skillBonus = match.groupValues[1].toInt(),
                craftingCategory = match.groupValues.getOrNull(2)?.trim()?.trimEnd('.')?.ifBlank { null },
            )
        }
        return null
    }
}

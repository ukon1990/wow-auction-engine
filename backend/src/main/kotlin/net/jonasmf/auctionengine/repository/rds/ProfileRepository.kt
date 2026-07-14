package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.ProfessionAllocation
import net.jonasmf.auctionengine.generated.model.ProfessionProfile
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTree
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTreeEntry
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTreeNode
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTreePrerequisite
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTreeTab
import net.jonasmf.auctionengine.generated.model.CharacterProfessionPreviewProfession
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.generated.model.ProfileCharacterProfession
import net.jonasmf.auctionengine.generated.model.ProfileCharacterProfessionSource
import net.jonasmf.auctionengine.generated.model.ProfileCharacterRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProfileRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val characterInsert = SimpleJdbcInsert(jdbcTemplate).withTableName("user_character").usingGeneratedKeyColumns("id")
    private val profileInsert = SimpleJdbcInsert(jdbcTemplate).withTableName("user_character_profession_profile").usingGeneratedKeyColumns("id")

    fun listCharacters(subject: String): List<ProfileCharacter> =
        jdbcTemplate.query(
            "SELECT id, region, realm_name, character_name, source_guid FROM user_character WHERE owner_subject = ? ORDER BY updated_at DESC, id DESC",
            { rs, _ -> ProfileCharacter(rs.getLong("id"), rs.getString("region"), rs.getString("realm_name"), rs.getString("character_name"), rs.getString("source_guid")) },
            subject,
        )

    fun createCharacter(subject: String, request: ProfileCharacterRequest): ProfileCharacter {
        val id = characterInsert.executeAndReturnKey(mapOf("owner_subject" to subject, "region" to request.region.trim(), "realm_name" to request.realmName.trim(), "character_name" to request.characterName.trim(), "source_guid" to request.sourceGuid?.trim()?.ifBlank { null })).toLong()
        return findCharacter(subject, id) ?: error("Created character was not found")
    }

    fun findCharacter(subject: String, id: Long): ProfileCharacter? =
        jdbcTemplate.query(
            "SELECT id, region, realm_name, character_name, source_guid FROM user_character WHERE id = ? AND owner_subject = ?",
            { rs, _ -> ProfileCharacter(rs.getLong("id"), rs.getString("region"), rs.getString("realm_name"), rs.getString("character_name"), rs.getString("source_guid")) },
            id,
            subject,
        ).firstOrNull()

    fun deleteCharacter(subject: String, id: Long): Boolean = jdbcTemplate.update("DELETE FROM user_character WHERE id = ? AND owner_subject = ?", id, subject) > 0

    fun listCharacterProfessions(subject: String, characterId: Long): List<ProfileCharacterProfession> =
        jdbcTemplate.query(
            """
            SELECT
                p.profession_id,
                p.skill_level,
                p.blizzard_synced_at,
                p.source_import_id,
                (
                    SELECT COUNT(*)
                    FROM user_character_profession_recipe recipe
                    WHERE recipe.profile_id = p.id AND recipe.learned = TRUE
                ) AS known_recipe_count,
                (
                    SELECT COUNT(*)
                    FROM user_character_profession_allocation allocation
                    WHERE allocation.profile_id = p.id
                ) AS allocation_count
            FROM user_character_profession_profile p
                INNER JOIN user_character character_row ON character_row.id = p.character_id
            WHERE character_row.owner_subject = ? AND character_row.id = ?
            ORDER BY p.profession_id
            """.trimIndent(),
            { rs, _ -> toProfileCharacterProfession(rs) },
            subject,
            characterId,
        )

    @Transactional
    fun syncBlizzardProfessions(
        characterId: Long,
        professions: List<CharacterProfessionPreviewProfession>,
    ): List<ProfileCharacterProfession> {
        professions.filter { professionExists(it.professionId) }.forEach { profession ->
            val skillLevel = profession.tiers.maxOfOrNull { it.skillPoints }
            jdbcTemplate.update(
                """
                INSERT INTO user_character_profession_profile (character_id, profession_id, skill_level, blizzard_synced_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    skill_level = GREATEST(COALESCE(skill_level, 0), COALESCE(VALUES(skill_level), 0)),
                    blizzard_synced_at = CURRENT_TIMESTAMP
                """.trimIndent(),
                characterId,
                profession.professionId,
                skillLevel,
            )
            val profileId =
                jdbcTemplate.queryForObject(
                    "SELECT id FROM user_character_profession_profile WHERE character_id = ? AND profession_id = ?",
                    Long::class.java,
                    characterId,
                    profession.professionId,
                )!!
            profession.tiers.flatMap { tier -> tier.knownRecipes }.forEach { recipe ->
                jdbcTemplate.update(
                    """
                    INSERT INTO user_character_profession_recipe
                        (profile_id, recipe_id, recipe_name, learned, source_import_id, blizzard_synced_at)
                    VALUES (?, ?, ?, TRUE, NULL, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        recipe_name = VALUES(recipe_name),
                        learned = learned OR VALUES(learned),
                        blizzard_synced_at = CURRENT_TIMESTAMP
                    """.trimIndent(),
                    profileId,
                    recipe.recipeId,
                    recipe.recipeName,
                )
            }
        }
        return listCharacterProfessionsByCharacterId(characterId)
    }

    private fun listCharacterProfessionsByCharacterId(characterId: Long): List<ProfileCharacterProfession> =
        jdbcTemplate.query(
            """
            SELECT
                p.profession_id,
                p.skill_level,
                p.blizzard_synced_at,
                p.source_import_id,
                (
                    SELECT COUNT(*)
                    FROM user_character_profession_recipe recipe
                    WHERE recipe.profile_id = p.id AND recipe.learned = TRUE
                ) AS known_recipe_count,
                (
                    SELECT COUNT(*)
                    FROM user_character_profession_allocation allocation
                    WHERE allocation.profile_id = p.id
                ) AS allocation_count
            FROM user_character_profession_profile p
            WHERE p.character_id = ?
            ORDER BY p.profession_id
            """.trimIndent(),
            { rs, _ -> toProfileCharacterProfession(rs) },
            characterId,
        )

    fun listTrees(expansionId: Int, professionId: Int): List<ProfessionSkillTree> {
        val trees = jdbcTemplate.query("SELECT id, expansion_id, profession_id, external_tree_id, name, description FROM profession_skill_tree WHERE expansion_id = ? AND profession_id = ? ORDER BY id", { rs, _ -> TreeRow(rs.getLong("id"), rs.getInt("expansion_id"), rs.getInt("profession_id"), rs.getInt("external_tree_id"), rs.getString("name"), rs.getString("description")) }, expansionId, professionId)
        if (trees.isEmpty()) return emptyList()
        val treeIds = trees.map(TreeRow::id)
        val tabs = jdbcTemplate.query("SELECT id, tree_id, external_tab_id, name, description, display_order FROM profession_skill_tree_tab WHERE tree_id IN (${treeIds.joinToString()}) ORDER BY display_order, id", { rs, _ -> TabRow(rs.getLong("id"), rs.getLong("tree_id"), rs.getInt("external_tab_id"), rs.getString("name"), rs.getString("description"), rs.getInt("display_order")) })
        val nodes = jdbcTemplate.query("SELECT id, tree_id, tab_id, external_node_id, name, description, max_ranks, required_rank, display_order FROM profession_skill_tree_node WHERE tree_id IN (${treeIds.joinToString()}) ORDER BY display_order, id", { rs, _ -> NodeRow(rs.getLong("id"), rs.getLong("tree_id"), rs.getObject("tab_id", Long::class.javaObjectType), rs.getInt("external_node_id"), rs.getString("name"), rs.getString("description"), rs.getInt("max_ranks"), rs.getInt("required_rank"), rs.getInt("display_order")) })
        val nodeIds = nodes.map(NodeRow::id)
        val entries = if (nodeIds.isEmpty()) emptyList() else jdbcTemplate.query("SELECT id, node_id, external_entry_id, name, description, rank_limit, display_order FROM profession_skill_tree_entry WHERE node_id IN (${nodeIds.joinToString()}) ORDER BY display_order, id", { rs, _ -> EntryRow(rs.getLong("id"), rs.getLong("node_id"), rs.getInt("external_entry_id"), rs.getString("name"), rs.getString("description"), rs.getInt("rank_limit"), rs.getInt("display_order")) })
        val parents = if (nodeIds.isEmpty()) emptyList() else jdbcTemplate.query("SELECT node_id, parent_node_id, required_parent_ranks FROM profession_skill_tree_node_parent WHERE node_id IN (${nodeIds.joinToString()})", { rs, _ -> ParentRow(rs.getLong("node_id"), rs.getLong("parent_node_id"), rs.getInt("required_parent_ranks")) })
        val entriesByNode = entries.groupBy(EntryRow::nodeId)
        val parentsByNode = parents.groupBy(ParentRow::nodeId)
        val nodesByTab = nodes.filter { it.tabId != null }.groupBy { it.tabId!! }
        return trees.map { tree -> ProfessionSkillTree(tree.id, tree.expansionId, tree.professionId, tree.externalTreeId, tree.name, tabs.filter { it.treeId == tree.id }.map { tab -> ProfessionSkillTreeTab(tab.id, tab.externalTabId, tab.name, tab.displayOrder, nodesByTab[tab.id].orEmpty().map { node -> node.toApi(entriesByNode[node.id].orEmpty(), parentsByNode[node.id].orEmpty()) }, tab.description) }, tree.description) }
    }

    fun getProfile(subject: String, characterId: Long, professionId: Int): ProfessionProfile? =
        jdbcTemplate.query("SELECT p.id, p.tree_id, p.skill_level FROM user_character_profession_profile p JOIN user_character c ON c.id = p.character_id WHERE p.character_id = ? AND p.profession_id = ? AND c.owner_subject = ?", { rs, _ -> ProfileRow(rs.getLong("id"), rs.getLong("tree_id"), rs.getObject("skill_level", Int::class.javaObjectType)) }, characterId, professionId, subject).firstOrNull()?.let { profile -> ProfessionProfile(characterId, professionId, allocations(profile.id), profile.treeId, profile.skillLevel) }

    @Transactional
    fun replaceProfile(subject: String, characterId: Long, professionId: Int, treeId: Long, skillLevel: Int?, allocations: List<ProfessionAllocation>): ProfessionProfile {
        val profileId = jdbcTemplate.query("SELECT p.id FROM user_character_profession_profile p JOIN user_character c ON c.id = p.character_id WHERE p.character_id = ? AND p.profession_id = ? AND c.owner_subject = ?", { rs, _ -> rs.getLong(1) }, characterId, professionId, subject).firstOrNull()
            ?: profileInsert.executeAndReturnKey(mapOf("character_id" to characterId, "profession_id" to professionId, "tree_id" to treeId, "skill_level" to skillLevel)).toLong()
        jdbcTemplate.update("UPDATE user_character_profession_profile SET tree_id = ?, skill_level = ? WHERE id = ?", treeId, skillLevel, profileId)
        jdbcTemplate.update("DELETE FROM user_character_profession_allocation WHERE profile_id = ?", profileId)
        allocations.forEach { allocation -> jdbcTemplate.update("INSERT INTO user_character_profession_allocation (profile_id, entry_id, rank) VALUES (?, ?, ?)", profileId, allocation.entryId, allocation.rank) }
        return ProfessionProfile(characterId, professionId, allocations.sortedBy(ProfessionAllocation::entryId), treeId, skillLevel)
    }

    fun deleteProfile(subject: String, characterId: Long, professionId: Int): Boolean = jdbcTemplate.update("DELETE p FROM user_character_profession_profile p JOIN user_character c ON c.id = p.character_id WHERE p.character_id = ? AND p.profession_id = ? AND c.owner_subject = ?", characterId, professionId, subject) > 0

    /**
     * Loads all configured profiles relevant to a crafting result in one query. A profile's tree
     * expansion is retained so callers cannot apply, for example, a Dragonflight allocation to a
     * Midnight recipe with the same profession id.
     */
    fun findCraftingCandidates(subject: String, professionIds: Collection<Int>): List<CraftingProfileCandidate> {
        if (professionIds.isEmpty()) return emptyList()
        val placeholders = professionIds.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT
                c.id AS character_id,
                c.character_name,
                c.region,
                c.realm_name,
                p.profession_id,
                t.expansion_id,
                p.skill_level,
                COUNT(a.entry_id) AS allocation_count
            FROM user_character_profession_profile p
                INNER JOIN user_character c ON c.id = p.character_id
                INNER JOIN profession_skill_tree t ON t.id = p.tree_id
                LEFT JOIN user_character_profession_allocation a ON a.profile_id = p.id
            WHERE c.owner_subject = ?
                AND p.profession_id IN ($placeholders)
            GROUP BY c.id, c.character_name, c.region, c.realm_name, p.profession_id, t.expansion_id, p.skill_level
            """.trimIndent(),
            { rs, _ ->
                CraftingProfileCandidate(
                    characterId = rs.getLong("character_id"),
                    characterName = rs.getString("character_name"),
                    region = rs.getString("region"),
                    realmName = rs.getString("realm_name"),
                    professionId = rs.getInt("profession_id"),
                    expansionId = rs.getInt("expansion_id"),
                    skillLevel = rs.getObject("skill_level", Int::class.javaObjectType),
                    allocationCount = rs.getInt("allocation_count"),
                )
            },
            subject,
            *professionIds.toTypedArray(),
        )
    }

    fun allocationRules(treeId: Long): List<AllocationRule> = jdbcTemplate.query("SELECT e.id entry_id, e.node_id, e.rank_limit, n.max_ranks, n.required_rank FROM profession_skill_tree_entry e JOIN profession_skill_tree_node n ON n.id = e.node_id WHERE n.tree_id = ?", { rs, _ -> AllocationRule(rs.getLong("entry_id"), rs.getLong("node_id"), rs.getInt("rank_limit"), rs.getInt("max_ranks"), rs.getInt("required_rank")) }, treeId)

    fun treeBelongsToProfession(
        treeId: Long,
        professionId: Int,
    ): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profession_skill_tree WHERE id = ? AND profession_id = ?",
            Long::class.java,
            treeId,
            professionId,
        )!! > 0

    private fun professionExists(professionId: Int): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profession WHERE id = ?",
            Long::class.java,
            professionId,
        )!! > 0

    fun parentRules(treeId: Long): List<ParentRule> = jdbcTemplate.query("SELECT p.node_id, p.parent_node_id, p.required_parent_ranks FROM profession_skill_tree_node_parent p JOIN profession_skill_tree_node n ON n.id = p.node_id WHERE n.tree_id = ?", { rs, _ -> ParentRule(rs.getLong("node_id"), rs.getLong("parent_node_id"), rs.getInt("required_parent_ranks")) }, treeId)

    private fun allocations(profileId: Long): List<ProfessionAllocation> = jdbcTemplate.query("SELECT entry_id, rank FROM user_character_profession_allocation WHERE profile_id = ? ORDER BY entry_id", { rs, _ -> ProfessionAllocation(rs.getLong("entry_id"), rs.getInt("rank")) }, profileId)
}

private fun toProfileCharacterProfession(rs: java.sql.ResultSet): ProfileCharacterProfession {
    val allocationCount = rs.getInt("allocation_count")
    val hasBlizzard = rs.getTimestamp("blizzard_synced_at") != null
    val hasAddon = rs.getObject("source_import_id", Long::class.javaObjectType) != null
    val sources =
        buildList {
            if (hasBlizzard) add(ProfileCharacterProfessionSource.BLIZZARD)
            if (hasAddon) add(ProfileCharacterProfessionSource.ADDON)
            if (allocationCount > 0 && !hasAddon) add(ProfileCharacterProfessionSource.MANUAL)
        }
    return ProfileCharacterProfession(
        professionId = rs.getInt("profession_id"),
        skillLevel = rs.getObject("skill_level", Int::class.javaObjectType),
        knownRecipeCount = rs.getInt("known_recipe_count"),
        allocationCount = allocationCount,
        sources = sources,
    )
}

private data class TreeRow(val id: Long, val expansionId: Int, val professionId: Int, val externalTreeId: Int, val name: String, val description: String?)
private data class TabRow(val id: Long, val treeId: Long, val externalTabId: Int, val name: String, val description: String?, val displayOrder: Int)
private data class NodeRow(val id: Long, val treeId: Long, val tabId: Long?, val externalNodeId: Int, val name: String?, val description: String?, val maxRanks: Int, val requiredRank: Int, val displayOrder: Int)
private data class EntryRow(val id: Long, val nodeId: Long, val externalEntryId: Int, val name: String?, val description: String?, val rankLimit: Int, val displayOrder: Int)
private data class ParentRow(val nodeId: Long, val parentNodeId: Long, val requiredParentRanks: Int)
data class AllocationRule(val entryId: Long, val nodeId: Long, val rankLimit: Int, val maxRanks: Int, val requiredRank: Int)
data class ParentRule(val nodeId: Long, val parentNodeId: Long, val requiredParentRanks: Int)
data class CraftingProfileCandidate(
    val characterId: Long,
    val characterName: String,
    val region: String,
    val realmName: String,
    val professionId: Int,
    val expansionId: Int,
    val skillLevel: Int?,
    val allocationCount: Int,
)
private data class ProfileRow(val id: Long, val treeId: Long, val skillLevel: Int?)

private fun NodeRow.toApi(
    entries: List<EntryRow>,
    parents: List<ParentRow>,
) = ProfessionSkillTreeNode(
    id = id,
    externalNodeId = externalNodeId,
    maxRanks = maxRanks,
    requiredRank = requiredRank,
    displayOrder = displayOrder,
    prerequisites = parents.map { ProfessionSkillTreePrerequisite(it.parentNodeId, it.requiredParentRanks) },
    propertyEntries =
        entries.map {
            ProfessionSkillTreeEntry(
                id = it.id,
                externalEntryId = it.externalEntryId,
                rankLimit = it.rankLimit,
                displayOrder = it.displayOrder,
                name = it.name,
                description = it.description,
            )
        },
    name = name,
    description = description,
)

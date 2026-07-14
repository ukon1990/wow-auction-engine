package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTree
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest

@Repository
class NormalizedProfessionImportRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val objectMapper = jacksonObjectMapper()

    fun save(
        payload: NormalizedAuctionHelperProfessionData,
        professionCount: Int,
        recipeCount: Int,
        ownerSubject: String = "admin",
    ) {
        val payloadJson = objectMapper.writeValueAsString(payload)
        val sourceFilesJson = objectMapper.writeValueAsString(payload.source.files)
        val contentHash = payloadJson.sha256()
        jdbcTemplate.update(
            """
            INSERT INTO normalized_profession_import (
                content_hash, contract_version, addon, addon_version, processor_version,
                source_files, character_count, profession_count, recipe_count, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                imported_at = CURRENT_TIMESTAMP,
                addon_version = VALUES(addon_version),
                processor_version = VALUES(processor_version),
                source_files = VALUES(source_files),
                character_count = VALUES(character_count),
                profession_count = VALUES(profession_count),
                recipe_count = VALUES(recipe_count),
                payload = VALUES(payload)
            """.trimIndent(),
            contentHash,
            payload.contractVersion.value,
            payload.source.addon.value,
            payload.source.addonVersion,
            payload.source.processorVersion,
            sourceFilesJson,
            payload.characters.size,
            professionCount,
            recipeCount,
            payloadJson,
        )
        val importId = jdbcTemplate.queryForObject("SELECT id FROM normalized_profession_import WHERE content_hash = ?", Long::class.java, contentHash)!!
        val treeImportId = saveTreeImport(payload, contentHash)
        val persistedTrees =
            payload.characters
                .flatMap { it.professions }
                .flatMap { it.talents?.trees.orEmpty().map { tree -> it.professionId to tree } }
                .distinctBy { (professionId, tree) -> professionId to tree.treeId }
                .associate { (professionId, tree) ->
                    (professionId to tree.treeId) to saveTreeDefinition(professionId, tree, treeImportId)
                }
        payload.characters.forEach { character ->
            jdbcTemplate.update(
                """INSERT INTO user_character (owner_subject, region, realm_name, character_name, source_guid)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE region = VALUES(region), realm_name = VALUES(realm_name),
                        character_name = VALUES(character_name), source_guid = VALUES(source_guid),
                        updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                ownerSubject,
                character.region.lowercase(),
                character.realm,
                character.name,
                character.characterKey,
            )
            val characterId =
                jdbcTemplate.queryForObject(
                    """SELECT id FROM user_character
                        WHERE owner_subject = ? AND region = ? AND realm_name = ? AND character_name = ?""".trimIndent(),
                    Long::class.java,
                    ownerSubject,
                    character.region.lowercase(),
                    character.realm,
                    character.name,
                )!!
            character.professions.forEach professionLoop@{ profession ->
                if (!exists("profession", profession.professionId)) return@professionLoop
                val selectedTree =
                    profession.talents
                        ?.trees
                        ?.filter { profession.activeSkillLineId == null || it.skillLineId == profession.activeSkillLineId }
                        ?.maxByOrNull { it.expansionId }
                val selectedTreeId = selectedTree?.let { persistedTrees[profession.professionId to it.treeId] }
                if (selectedTreeId == null) {
                    jdbcTemplate.update(
                        """INSERT INTO user_character_profession_profile (character_id, profession_id, skill_level)
                            VALUES (?, ?, ?)
                            ON DUPLICATE KEY UPDATE skill_level = VALUES(skill_level), updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                        characterId,
                        profession.professionId,
                        profession.skillLevel,
                    )
                } else {
                    jdbcTemplate.update(
                        """INSERT INTO user_character_profession_profile
                                (character_id, profession_id, skill_level, tree_id, source_import_id)
                            VALUES (?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE skill_level = VALUES(skill_level), tree_id = VALUES(tree_id),
                                source_import_id = VALUES(source_import_id), updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                        characterId,
                        profession.professionId,
                        profession.skillLevel,
                        selectedTreeId,
                        treeImportId,
                    )
                }
                val profileId =
                    jdbcTemplate.queryForObject(
                        "SELECT id FROM user_character_profession_profile WHERE character_id = ? AND profession_id = ?",
                        Long::class.java,
                        characterId,
                        profession.professionId,
                    )!!
                profession.recipes.forEach { recipe ->
                    jdbcTemplate.update(
                        """INSERT INTO user_character_profession_recipe
                            (profile_id, recipe_id, recipe_name, learned, source_import_id)
                            VALUES (?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE recipe_name = VALUES(recipe_name), learned = VALUES(learned),
                                source_import_id = VALUES(source_import_id), updated_at = CURRENT_TIMESTAMP""".trimIndent(),
                        profileId,
                        recipe.recipeId,
                        recipe.name,
                        recipe.learned,
                        importId,
                    )
                }
                if (selectedTree != null && selectedTreeId != null) {
                    jdbcTemplate.update("DELETE FROM user_character_profession_allocation WHERE profile_id = ?", profileId)
                    saveAllocations(profileId, selectedTreeId, profession.talents.allocations)
                }
            }
        }
    }

    private fun saveTreeImport(
        payload: NormalizedAuctionHelperProfessionData,
        contentHash: String,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO profession_tree_import
                    (source_type, source_version, addon_version, schema_version, content_hash)
                VALUES ('AuctionHelper-normalized', ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), imported_at = CURRENT_TIMESTAMP""".trimIndent(),
            payload.source.processorVersion,
            payload.source.addonVersion,
            payload.contractVersion.value,
            contentHash,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun saveTreeDefinition(
        professionId: Int,
        tree: NormalizedAuctionHelperTalentTree,
        importId: Long,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO profession_skill_tree
                    (expansion_id, profession_id, skill_line_id, config_id, external_tree_id, name, import_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), expansion_id = VALUES(expansion_id),
                    skill_line_id = VALUES(skill_line_id), external_tree_id = VALUES(external_tree_id),
                    name = VALUES(name), import_id = VALUES(import_id)""".trimIndent(),
            tree.expansionId,
            professionId,
            tree.skillLineId,
            tree.treeId.toLong(),
            tree.treeId,
            tree.name ?: "Profession $professionId specialization ${tree.treeId}",
            importId,
        )
        val databaseTreeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        val databaseNodeIds = mutableMapOf<Int, Long>()
        tree.tabs.forEachIndexed { tabOrder, tab ->
            jdbcTemplate.update(
                """INSERT INTO profession_skill_tree_tab
                        (tree_id, external_tab_id, name, description, display_order)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name),
                        description = VALUES(description), display_order = VALUES(display_order)""".trimIndent(),
                databaseTreeId,
                tab.tabId,
                tab.name ?: "Specialization ${tab.tabId}",
                tab.description,
                tabOrder,
            )
            val databaseTabId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
            tab.nodes.forEachIndexed { nodeOrder, node ->
                jdbcTemplate.update(
                    """INSERT INTO profession_skill_tree_node
                            (tree_id, tab_id, external_node_id, name, description, max_ranks, required_rank, display_order)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), tab_id = VALUES(tab_id), name = VALUES(name),
                            description = VALUES(description), max_ranks = VALUES(max_ranks),
                            required_rank = VALUES(required_rank), display_order = VALUES(display_order)""".trimIndent(),
                    databaseTreeId,
                    databaseTabId,
                    node.nodeId,
                    node.name ?: "Node ${node.nodeId}",
                    node.description,
                    node.maxRanks ?: 1,
                    node.requiredRank ?: 0,
                    nodeOrder,
                )
                val databaseNodeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
                databaseNodeIds[node.nodeId] = databaseNodeId
                node.propertyEntries.forEachIndexed { entryOrder, entry ->
                    jdbcTemplate.update(
                        """INSERT INTO profession_skill_tree_entry
                                (node_id, external_entry_id, name, description, rank_limit, display_order)
                            VALUES (?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name),
                                description = VALUES(description), rank_limit = VALUES(rank_limit),
                                display_order = VALUES(display_order)""".trimIndent(),
                        databaseNodeId,
                        entry.entryId,
                        entry.name ?: "Entry ${entry.entryId}",
                        entry.description,
                        entry.rankLimit ?: node.maxRanks ?: 1,
                        entryOrder,
                    )
                }
            }
        }
        jdbcTemplate.update(
            """DELETE parent FROM profession_skill_tree_node_parent parent
                JOIN profession_skill_tree_node node ON node.id = parent.node_id
                WHERE node.tree_id = ?""".trimIndent(),
            databaseTreeId,
        )
        tree.tabs
            .flatMap { it.nodes }
            .forEach { node ->
                val databaseNodeId = databaseNodeIds.getValue(node.nodeId)
                node.parentNodeIds.orEmpty().forEach { parentNodeId ->
                    jdbcTemplate.update(
                        """INSERT INTO profession_skill_tree_node_parent
                                (node_id, parent_node_id, required_parent_ranks)
                            VALUES (?, ?, 1)""".trimIndent(),
                        databaseNodeId,
                        databaseNodeIds.getValue(parentNodeId),
                    )
                }
            }
        return databaseTreeId
    }

    private fun saveAllocations(
        profileId: Long,
        treeId: Long,
        allocations: List<net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentAllocation>,
    ) {
        allocations.filter { it.rank > 0 }.forEach { allocation ->
            jdbcTemplate.update(
                """INSERT INTO user_character_profession_allocation (profile_id, entry_id, rank)
                    SELECT ?, e.id, ?
                    FROM profession_skill_tree_entry e
                    JOIN profession_skill_tree_node n ON n.id = e.node_id
                    WHERE n.tree_id = ? AND n.external_node_id = ? AND e.external_entry_id = ?""".trimIndent(),
                profileId,
                allocation.rank,
                treeId,
                allocation.nodeId,
                allocation.entryId,
            )
        }
    }

    private fun exists(table: String, id: Int): Boolean =
        jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM $table WHERE id = ?)", Boolean::class.java, id) == true

    fun missingProfessionIds(professionIds: Set<Int>): Set<Int> = professionIds.filterNot { exists("profession", it) }.toSet()

    fun missingSkillLineIds(skillLineIds: Set<Int>): Set<Int> = skillLineIds.filterNot { exists("skill_tier", it) }.toSet()

    fun missingExpansionIds(expansionIds: Set<Int>): Set<Int> = expansionIds.filterNot { exists("expansion", it) }.toSet()
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

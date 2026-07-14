package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest

@Repository
class ProfessionTalentTreeImportRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun replace(
        payload: JsonNode,
        contentHash: String = payload.toString().sha256(),
    ): Int {
        val source = payload.requiredText("source")
        val importId = insertImport(source, payload, contentHash)
        val trees = payload.required("trees")
        require(trees.isArray && trees.size() > 0) { "trees must contain at least one tree" }
        trees.forEach { tree -> replaceTree(tree, importId) }
        return trees.size()
    }

    private fun insertImport(
        source: String,
        payload: JsonNode,
        contentHash: String,
    ): Long {
        jdbcTemplate.update(
            """
            INSERT INTO profession_tree_import (source_type, source_version, addon_version, schema_version, game_build, content_hash)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), imported_at = CURRENT_TIMESTAMP
            """.trimIndent(),
            source,
            payload.textOrNull("sourceVersion"),
            payload.textOrNull("addonVersion"),
            payload.intOrNull("schemaVersion"),
            payload.textOrNull("gameBuild"),
            contentHash,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun replaceTree(tree: JsonNode, importId: Long) {
        val expansionId = tree.requiredInt("expansionId")
        val professionId = tree.requiredInt("professionId")
        val externalTreeId = tree.requiredInt("externalTreeId")
        jdbcTemplate.update(
            """
            UPDATE user_character_profession_profile p
            JOIN profession_skill_tree t ON t.id = p.tree_id
            SET p.tree_id = NULL
            WHERE t.expansion_id = ? AND t.profession_id = ? AND t.external_tree_id = ?
            """.trimIndent(), expansionId, professionId, externalTreeId,
        )
        jdbcTemplate.update(
            "DELETE FROM profession_skill_tree WHERE expansion_id = ? AND profession_id = ? AND external_tree_id = ?",
            expansionId, professionId, externalTreeId,
        )
        jdbcTemplate.update(
            """INSERT INTO profession_skill_tree (expansion_id, profession_id, external_tree_id, name, description, import_id)
               VALUES (?, ?, ?, ?, ?, ?)""",
            expansionId, professionId, externalTreeId, tree.requiredText("name"), tree.textOrNull("description"), importId,
        )
        val treeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        tree.required("tabs").forEachIndexed { tabOrder, tab -> insertTab(treeId, tab, tabOrder) }
    }

    private fun insertTab(treeId: Long, tab: JsonNode, displayOrder: Int) {
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree_tab (tree_id, external_tab_id, name, description, display_order) VALUES (?, ?, ?, ?, ?)",
            treeId, tab.requiredInt("externalTabId"), tab.requiredText("name"), tab.textOrNull("description"), displayOrder,
        )
        val tabId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        tab.required("nodes").forEachIndexed { nodeOrder, node -> insertNode(treeId, tabId, node, nodeOrder) }
    }

    private fun insertNode(treeId: Long, tabId: Long, node: JsonNode, displayOrder: Int) {
        val maxRanks = node.requiredInt("maxRanks")
        val requiredRank = node.intOrNull("requiredRank") ?: 0
        require(requiredRank in 0..maxRanks) { "requiredRank must be between zero and maxRanks" }
        jdbcTemplate.update(
            """INSERT INTO profession_skill_tree_node (tree_id, tab_id, external_node_id, name, description, max_ranks, required_rank, display_order)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            treeId, tabId, node.requiredInt("externalNodeId"), node.requiredText("name"), node.textOrNull("description"), maxRanks, requiredRank, displayOrder,
        )
        val nodeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        node.required("entries").forEachIndexed { entryOrder, entry ->
            jdbcTemplate.update(
                "INSERT INTO profession_skill_tree_entry (node_id, external_entry_id, name, description, rank_limit, display_order) VALUES (?, ?, ?, ?, ?, ?)",
                nodeId, entry.requiredInt("externalEntryId"), entry.requiredText("name"), entry.textOrNull("description"), entry.requiredInt("rankLimit"), entryOrder,
            )
        }
    }
}

private fun JsonNode.required(name: String): JsonNode = get(name) ?: throw IllegalArgumentException("Missing $name")
private fun JsonNode.requiredText(name: String): String = required(name).asText().takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing $name")
private fun JsonNode.requiredInt(name: String): Int = required(name).takeIf { it.isInt }?.intValue() ?: throw IllegalArgumentException("$name must be an integer")
private fun JsonNode.textOrNull(name: String): String? = get(name)?.takeIf { it.isTextual }?.textValue()
private fun JsonNode.intOrNull(name: String): Int? = get(name)?.takeIf { it.isInt }?.intValue()
private fun String.sha256(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

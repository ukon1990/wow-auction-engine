package net.jonasmf.auctionengine.testsupport

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperCharacter
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSource
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperSourceFilesInner
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentEntry
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTab
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentTree
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalents
import org.springframework.jdbc.core.JdbcTemplate

object ProfessionProfileTestFixtures {
    const val MIDNIGHT_EXPANSION_ID = 12
    const val BLACKSMITHING_PROFESSION_ID = 164
    const val BLACKSMITHING_SKILL_LINE_ID = 2907

    data class SeededProfessionTree(
        val treeId: Long,
        val professionId: Int,
        val expansionId: Int,
        val entryId: Long,
        val externalNodeId: Int,
    )

    fun seedMidnightCatalog(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.update("INSERT INTO profession (id) VALUES ($BLACKSMITHING_PROFESSION_ID)")
        jdbcTemplate.update(
            "INSERT INTO skill_tier (id, minimum_skill_level, maximum_skill_level, profession_id) VALUES ($BLACKSMITHING_SKILL_LINE_ID, 1, 100, $BLACKSMITHING_PROFESSION_ID)",
        )
        jdbcTemplate.update(
            "INSERT INTO locale (source_type, source_key, source_field, en_us) VALUES ('expansion', '$MIDNIGHT_EXPANSION_ID', 'name', 'Midnight')",
        )
        jdbcTemplate.update(
            """
            INSERT INTO expansion (id, slug, name_id, major_version, display_order)
            VALUES ($MIDNIGHT_EXPANSION_ID, 'midnight', (
                SELECT id FROM locale WHERE source_type = 'expansion' AND source_key = '$MIDNIGHT_EXPANSION_ID'
            ), $MIDNIGHT_EXPANSION_ID, 120)
            """.trimIndent(),
        )
    }

    fun seedMinimalProfessionTree(jdbcTemplate: JdbcTemplate): SeededProfessionTree {
        seedMidnightCatalog(jdbcTemplate)
        jdbcTemplate.update(
            "INSERT INTO profession_tree_import (source_type, content_hash) VALUES ('test', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')",
        )
        jdbcTemplate.update(
            """
            INSERT INTO profession_skill_tree (expansion_id, profession_id, external_tree_id, name, import_id)
            VALUES ($MIDNIGHT_EXPANSION_ID, $BLACKSMITHING_PROFESSION_ID, 82167859, 'Midnight Blacksmithing', 1)
            """.trimIndent(),
        )
        val treeId =
            jdbcTemplate.queryForObject(
                "SELECT id FROM profession_skill_tree WHERE external_tree_id = 82167859",
                Long::class.java,
            )!!
        jdbcTemplate.update(
            """
            INSERT INTO profession_skill_tree_tab (tree_id, external_tab_id, name, display_order)
            VALUES ($treeId, 1068, 'Craftsmithing', 0)
            """.trimIndent(),
        )
        val tabId = jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree_tab WHERE tree_id = ?", Long::class.java, treeId)!!
        jdbcTemplate.update(
            """
            INSERT INTO profession_skill_tree_node (tree_id, tab_id, external_node_id, name, max_ranks, required_rank, display_order)
            VALUES ($treeId, $tabId, 104229, 'Craftsmithing', 30, 0, 0)
            """.trimIndent(),
        )
        val nodeId = jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree_node WHERE external_node_id = 104229", Long::class.java)!!
        jdbcTemplate.update(
            """
            INSERT INTO profession_skill_tree_entry (node_id, external_entry_id, name, rank_limit, display_order)
            VALUES ($nodeId, 128829, 'Craftsmithing', 30, 0)
            """.trimIndent(),
        )
        val entryId = jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree_entry WHERE external_entry_id = 128829", Long::class.java)!!
        return SeededProfessionTree(
            treeId = treeId,
            professionId = BLACKSMITHING_PROFESSION_ID,
            expansionId = MIDNIGHT_EXPANSION_ID,
            entryId = entryId,
            externalNodeId = 104229,
        )
    }

    fun insertOwnedCharacter(
        jdbcTemplate: JdbcTemplate,
        ownerSubject: String,
        characterName: String,
        region: String = "eu",
        realmName: String = "Argent Dawn",
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO user_character (owner_subject, region, realm_name, character_name) VALUES (?, ?, ?, ?)",
            ownerSubject,
            region,
            realmName,
            characterName,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM user_character WHERE owner_subject = ? AND character_name = ?",
            Long::class.java,
            ownerSubject,
            characterName,
        )!!
    }

    fun normalizedImportPayload(characters: List<NormalizedAuctionHelperCharacter> = emptyList()): NormalizedAuctionHelperProfessionData =
        NormalizedAuctionHelperProfessionData(
            contractVersion = NormalizedAuctionHelperProfessionData.ContractVersion._1,
            source =
                NormalizedAuctionHelperSource(
                    addon = NormalizedAuctionHelperSource.Addon.AUCTION_HELPER,
                    addonVersion = "2.0.0",
                    processorVersion = "test",
                    files =
                        listOf(
                            NormalizedAuctionHelperSourceFilesInner(
                                fileName = "AuctionHelper_Professions.lua",
                                sha256 = "c".repeat(64),
                            ),
                        ),
                ),
            characters = characters,
        )

    fun blacksmithingTreeWithSingleNode(): NormalizedAuctionHelperTalentTree =
        NormalizedAuctionHelperTalentTree(
            treeId = 82167859,
            skillLineId = BLACKSMITHING_SKILL_LINE_ID,
            expansionId = MIDNIGHT_EXPANSION_ID,
            name = "Midnight Blacksmithing",
            tabs =
                listOf(
                    NormalizedAuctionHelperTalentTab(
                        tabId = 1068,
                        name = "Craftsmithing",
                        description = "Craft professional equipment.",
                        nodes =
                            listOf(
                                NormalizedAuctionHelperTalentNode(
                                    nodeId = 104229,
                                    maxRanks = 30,
                                    name = "Craftsmithing",
                                    propertyEntries =
                                        listOf(
                                            NormalizedAuctionHelperTalentEntry(
                                                entryId = 128829,
                                                rankLimit = 30,
                                                name = "Craftsmithing",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    fun characterWithBlacksmithingRecipe(
        characterKey: String = "eu-argent-crafter",
        characterName: String = "Crafter",
        realm: String = "Argent Dawn",
        region: String = "eu",
        recipeId: Int = 450216,
        qualityThresholds: List<java.math.BigDecimal> = listOf(50.toBigDecimal(), 100.toBigDecimal(), 150.toBigDecimal()),
    ): NormalizedAuctionHelperCharacter {
        val recipe =
            NormalizedAuctionHelperRecipe(
                recipeId = recipeId,
                name = "Charged Claymore",
                learned = true,
                qualityOutputItemIds = emptyList(),
                qualityThresholds = qualityThresholds,
                reagentSlots = emptyList(),
                maxQualityRequiredReagents = emptyList(),
                baseSkill = 50.toBigDecimal(),
            )
        val profession =
            NormalizedAuctionHelperProfession(
                professionId = BLACKSMITHING_PROFESSION_ID,
                name = "Blacksmithing",
                recipes = listOf(recipe),
                skillLineId = BLACKSMITHING_SKILL_LINE_ID,
                skillLevel = 85,
                maxSkillLevel = 100,
                activeSkillLineId = BLACKSMITHING_SKILL_LINE_ID,
                talents = NormalizedAuctionHelperTalents(listOf(blacksmithingTreeWithSingleNode()), emptyList()),
            )
        return NormalizedAuctionHelperCharacter(characterKey, characterName, realm, region, listOf(profession))
    }
}

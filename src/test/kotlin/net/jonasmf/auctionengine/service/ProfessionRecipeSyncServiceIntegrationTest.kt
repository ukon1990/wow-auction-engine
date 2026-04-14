package net.jonasmf.auctionengine.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.repository.rds.RecipeRepository
import net.jonasmf.auctionengine.testsupport.container.SharedTestContainers
import net.jonasmf.auctionengine.testsupport.database.TestDataCleaner
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@SpringBootTest
@ActiveProfiles("test")
@Import(ProfessionRecipeSyncServiceIntegrationTest.FixtureWebClientConfig::class)
class ProfessionRecipeSyncServiceIntegrationTest {
    @Autowired
    lateinit var professionRecipeSyncService: ProfessionRecipeSyncService

    @Autowired
    lateinit var recipeRepository: RecipeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var testDataCleaner: TestDataCleaner

    @BeforeEach
    fun resetDatabase() {
        testDataCleaner.resetRelationalDatabase()
    }

    @Test
    fun `syncRegion downloads and persists full profession recipe graph`() {
        val result = professionRecipeSyncService.syncRegion(Region.Europe)
        val categoryMetadataLocaleTable = referencedLocaleTable("modified_crafting_category_metadata", "name_id")
        val slotMetadataLocaleTable = referencedLocaleTable("modified_crafting_slot_metadata", "description_id")

        assertEquals(1, result.professionsFetched)
        assertEquals(1, result.skillTiersFetched)
        assertEquals(2, result.recipeReferencesDiscovered)
        assertEquals(2, result.recipesFetched)
        assertEquals(2, result.modifiedCraftingCategoriesFetched)
        assertEquals(2, result.modifiedCraftingSlotsFetched)

        assertEquals(1, countRows("profession"))
        assertEquals(1, countRows("skill_tier"))
        assertEquals(2, countRows("profession_category"))
        assertEquals(2, countRows("recipe"))
        assertEquals(4, countRows("recipe_reagent"))
        assertEquals(2, countRows("modified_crafting_slot"))
        assertEquals(2, countRows("modified_crafting_category_metadata"))
        assertEquals(2, countRows("modified_crafting_slot_metadata"))
        assertEquals(3, countRows("modified_crafting_slot_metadata_category"))
        assertEquals(
            2,
            countRowsWhere(
                categoryMetadataLocaleTable,
                "source_type = 'modified_crafting_category_metadata' AND source_field = 'name'",
            ),
        )
        assertEquals(
            2,
            countRowsWhere(
                slotMetadataLocaleTable,
                "source_type = 'modified_crafting_slot_metadata' AND source_field = 'description'",
            ),
        )
        assertEquals(2, recipeRepository.findDistinctCraftedItemIds().size)
        assertEquals(2, recipeRepository.findDistinctReagentItemIds().size)
        assertEquals(4, recipeRepository.findDistinctReferencedItemIds().size)
    }

    private fun countRows(tableName: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Int::class.java)!!

    private fun countRowsWhere(
        tableName: String,
        condition: String,
    ): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE $condition", Int::class.java)!!

    private fun referencedLocaleTable(
        tableName: String,
        columnName: String,
    ): String =
        jdbcTemplate.queryForObject(
            """
            SELECT referenced_table_name
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
              AND referenced_table_name IS NOT NULL
            """.trimIndent(),
            String::class.java,
            tableName,
            columnName,
        )!!

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            SharedTestContainers.registerMariaDbProperties(registry)
            registry.add("blizzard.base-url") { "api.blizzard.test/data/wow/" }
            registry.add("blizzard.token-url") { "https://oauth.blizzard.test/token" }
            registry.add("blizzard.client-id") { "id" }
            registry.add("blizzard.client-secret") { "secret" }
            registry.add("blizzard.regions") { "Europe" }
            registry.add("app.scheduling.enabled") { "false" }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FixtureWebClientConfig {
        @Bean
        @Primary
        fun fixtureWebClient(): WebClient =
            WebClient
                .builder()
                .exchangeFunction(ExchangeFunction(::handleRequest))
                .build()

        private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
            val path = request.url().path
            return when {
                request.method() == HttpMethod.POST -> {
                    jsonResponse("""{"access_token":"stub-token","expires_in":3600,"token_type":"Bearer"}""")
                }
                path.endsWith("/data/wow/profession/index") -> jsonResponse(trimmedProfessionIndex())
                path.matches(Regex(".*/data/wow/profession/\\d+$")) -> jsonResponse(trimmedProfessionDetail())
                path.matches(Regex(".*/data/wow/profession/\\d+/skill-tier/\\d+$")) -> jsonResponse(trimmedSkillTier())
                path.matches(
                    Regex(".*/data/wow/recipe/\\d+$"),
                ) -> jsonResponse(fixture("/blizzard/recipe/${path.substringAfterLast('/')}-response.json"))
                path.endsWith(
                    "/data/wow/modified-crafting/index",
                ) -> jsonResponse(fixture("/blizzard/modified-crafting/category/827-response.json"))
                path.endsWith(
                    "/data/wow/modified-crafting/category/index",
                ) -> jsonResponse(modifiedCraftingCategoryIndex())
                path.matches(Regex(".*/data/wow/modified-crafting/category/\\d+$")) -> {
                    jsonResponse(
                        fixture("/blizzard/modified-crafting/category/${path.substringAfterLast('/')}-response.json"),
                    )
                }
                path.endsWith(
                    "/data/wow/modified-crafting/reagent-slot-type/index",
                ) -> jsonResponse(modifiedCraftingSlotTypeIndex())
                path.matches(Regex(".*/data/wow/modified-crafting/reagent-slot-type/\\d+$")) -> {
                    jsonResponse(
                        fixture(
                            "/blizzard/modified-crafting/reagent-slot-type/${path.substringAfterLast(
                                '/',
                            )}-response.json",
                        ),
                    )
                }
                else -> error("Unexpected request: ${request.method()} ${request.url()}")
            }
        }

        private fun jsonResponse(body: String): Mono<ClientResponse> =
            Mono.just(
                ClientResponse
                    .create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build(),
            )

        private fun fixture(path: String): String = loadFixture(this, path)

        private fun modifiedCraftingCategoryIndex(): String =
            referencesIndexJson(
                selfHref = "https://eu.api.blizzard.test/data/wow/modified-crafting/category/index?namespace=static-eu",
                fieldName = "categories",
                detailPaths =
                    listOf(
                        "src/test/resources/blizzard/modified-crafting/category/29-response.json",
                        "src/test/resources/blizzard/modified-crafting/category/45-response.json",
                    ),
                hrefBase = "https://eu.api.blizzard.test/data/wow/modified-crafting/category",
                labelFieldName = "name",
            )

        private fun modifiedCraftingSlotTypeIndex(): String =
            referencesIndexJson(
                selfHref =
                    "https://eu.api.blizzard.test/data/wow/modified-crafting/reagent-slot-type/index?namespace=static-eu",
                fieldName = "slot_types",
                detailPaths =
                    listOf(
                        "src/test/resources/blizzard/modified-crafting/reagent-slot-type/46-response.json",
                        "src/test/resources/blizzard/modified-crafting/reagent-slot-type/77-response.json",
                    ),
                hrefBase = "https://eu.api.blizzard.test/data/wow/modified-crafting/reagent-slot-type",
                labelFieldName = "description",
            )

        private fun referencesIndexJson(
            selfHref: String,
            fieldName: String,
            detailPaths: List<String>,
            hrefBase: String,
            labelFieldName: String,
        ): String {
            val entries =
                detailPaths.joinToString(",\n") { path ->
                    val payload = fixture(path.removePrefix("src/test/resources"))
                    val id =
                        Regex("\"id\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)
                            ?: error("Missing id in fixture $path")
                    val nameJson =
                        Regex("\"$labelFieldName\"\\s*:\\s*(\\{.*?\\})", setOf(RegexOption.DOT_MATCHES_ALL))
                            .find(payload)
                            ?.groupValues
                            ?.get(1)
                            ?: error("Missing $labelFieldName in fixture $path")
                    """
                    {
                      "key": {
                        "href": "$hrefBase/$id?namespace=static-eu"
                      },
                      "name": $nameJson,
                      "id": $id
                    }
                    """.trimIndent()
                }

            return """
                {
                  "_links": {
                    "self": {
                      "href": "$selfHref"
                    }
                  },
                  "$fieldName": [
                    $entries
                  ]
                }
                """.trimIndent()
        }

        private fun trimmedProfessionIndex(): String =
            trimArrayFixture(
                fixturePath = "/blizzard/profession/index-response.json",
                arrayField = "professions",
                allowedIds = setOf(164),
            )

        private fun trimmedProfessionDetail(): String =
            trimArrayFixture(
                fixturePath = "/blizzard/profession/164-response.json",
                arrayField = "skill_tiers",
                allowedIds = setOf(2751),
            )

        private fun trimmedSkillTier(): String {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(fixture("/blizzard/profession/164/skill-tier/2751-response.json")) as ObjectNode
            val categories = root.withArray("categories")
            val filteredCategories = mapper.createArrayNode()
            val allowedRecipeIds = setOf(42363, 42368)

            for (index in 0 until categories.size()) {
                val categoryNode = categories.get(index) as ObjectNode
                val recipes = categoryNode.withArray("recipes")
                val filteredRecipes = mapper.createArrayNode()
                for (recipeIndex in 0 until recipes.size()) {
                    val recipeNode = recipes.get(recipeIndex)
                    if (recipeNode.path("id").asInt() in allowedRecipeIds) {
                        filteredRecipes.add(recipeNode)
                    }
                }
                if (!filteredRecipes.isEmpty) {
                    val categoryCopy: ObjectNode = categoryNode.deepCopy()
                    categoryCopy.set<com.fasterxml.jackson.databind.JsonNode>("recipes", filteredRecipes)
                    filteredCategories.add(categoryCopy)
                }
            }

            val copy: ObjectNode = root.deepCopy()
            copy.set<com.fasterxml.jackson.databind.JsonNode>("categories", filteredCategories)
            return mapper.writeValueAsString(copy)
        }

        private fun trimArrayFixture(
            fixturePath: String,
            arrayField: String,
            allowedIds: Set<Int>,
        ): String {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(fixture(fixturePath)) as ObjectNode
            val filtered = mapper.createArrayNode()
            val originalArray = root.withArray(arrayField)
            for (index in 0 until originalArray.size()) {
                val node = originalArray.get(index)
                if (node.path("id").asInt() in allowedIds) {
                    filtered.add(node)
                }
            }
            val copy: ObjectNode = root.deepCopy()
            copy.set<com.fasterxml.jackson.databind.JsonNode>(arrayField, filtered)
            return mapper.writeValueAsString(copy)
        }
    }
}

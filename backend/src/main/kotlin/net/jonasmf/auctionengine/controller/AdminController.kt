package net.jonasmf.auctionengine.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AdminExpansion1
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.AdminItem1
import net.jonasmf.auctionengine.generated.model.AdminItemBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminItemCreateRequest
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminItemPage
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.AdminRecipeAssociationRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipe1
import net.jonasmf.auctionengine.generated.model.AdminRecipeBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipePage
import net.jonasmf.auctionengine.generated.model.AdminRecipeSearchResult
import net.jonasmf.auctionengine.generated.model.AdminSqlExecuteRequest
import net.jonasmf.auctionengine.generated.model.AdminSqlMetadata
import net.jonasmf.auctionengine.generated.model.AdminSqlResult
import net.jonasmf.auctionengine.generated.model.AdminStatus
import net.jonasmf.auctionengine.generated.model.AuctionHelperTalentTreeLuaImportResult
import net.jonasmf.auctionengine.generated.model.AuctionHelperTalentTreeLuaImportResultDiagnosticsInner
import net.jonasmf.auctionengine.generated.model.AuctionHelperSavedVariablesInspection
import net.jonasmf.auctionengine.generated.model.AuctionHelperSavedVariablesInspectionSourcesInner
import net.jonasmf.auctionengine.generated.model.AuctionHelperSavedVariablesInspectionTalentExport
import net.jonasmf.auctionengine.generated.model.ProfessionTalentTreeImportRequest
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.service.admin.AdminExpansionService
import net.jonasmf.auctionengine.service.admin.AdminItemService
import net.jonasmf.auctionengine.service.admin.AdminJobService
import net.jonasmf.auctionengine.service.admin.AdminProfessionSyncService
import net.jonasmf.auctionengine.service.admin.AdminRecipeService
import net.jonasmf.auctionengine.service.admin.AdminSqlService
import net.jonasmf.auctionengine.service.admin.AdminStatusService
import net.jonasmf.auctionengine.service.admin.ProfessionTalentTreeImportService
import net.jonasmf.auctionengine.service.admin.ProfessionTalentTreeLuaImportService
import net.jonasmf.auctionengine.service.admin.AuctionHelperSavedVariablesInspectionService
import net.jonasmf.auctionengine.service.MAX_AUCTION_HELPER_IMPORT_BYTES
import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class AdminController(
    private val userService: UserService,
    private val adminStatusService: AdminStatusService,
    private val adminSqlService: AdminSqlService,
    private val adminExpansionService: AdminExpansionService,
    private val adminJobService: AdminJobService,
    private val adminProfessionSyncService: AdminProfessionSyncService,
    private val adminItemService: AdminItemService,
    private val adminRecipeService: AdminRecipeService,
    private val professionTalentTreeImportService: ProfessionTalentTreeImportService,
    private val professionTalentTreeLuaImportService: ProfessionTalentTreeLuaImportService,
    private val auctionHelperSavedVariablesInspectionService: AuctionHelperSavedVariablesInspectionService,
) : AdminApi {
    private val objectMapper = jacksonObjectMapper()
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminStatus(): ResponseEntity<AdminStatus> = ResponseEntity.ok(adminStatusService.getStatus())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun executeAdminSql(body: AdminSqlExecuteRequest): ResponseEntity<AdminSqlResult> =
        ResponseEntity.ok(adminSqlService.execute(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminSqlMetadata(): ResponseEntity<AdminSqlMetadata> =
        ResponseEntity.ok(adminSqlService.getMetadata())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansions(locale: String?): ResponseEntity<List<AdminExpansion1>> =
        ResponseEntity.ok(adminExpansionService.listExpansions(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansion(body: AdminExpansionRequest): ResponseEntity<AdminExpansion1> =
        ResponseEntity.ok(adminExpansionService.createExpansion(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun updateExpansion(
        id: Int,
        body: AdminExpansionRequest,
    ): ResponseEntity<AdminExpansion1> = ResponseEntity.ok(adminExpansionService.updateExpansion(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteExpansion(id: Int): ResponseEntity<Unit> {
        adminExpansionService.deleteExpansion(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansionRanges(locale: String?): ResponseEntity<List<AdminExpansionItemRange>> =
        ResponseEntity.ok(adminExpansionService.listRanges(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansionRange(body: AdminExpansionItemRangeRequest): ResponseEntity<AdminExpansionItemRange> =
        ResponseEntity.ok(adminExpansionService.createRange(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun updateExpansionRange(
        id: Long,
        body: AdminExpansionItemRangeRequest,
    ): ResponseEntity<AdminExpansionItemRange> = ResponseEntity.ok(adminExpansionService.updateRange(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteExpansionRange(id: Long): ResponseEntity<Unit> {
        adminExpansionService.deleteRange(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun applyExpansionRanges(): ResponseEntity<AdminJob> =
        ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(adminExpansionService.applyExpansionRanges(requestedBy()))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun fetchMissingExpansionRangeItems(): ResponseEntity<AdminJob> =
        ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(adminExpansionService.fetchMissingExpansionRangeItems(requestedBy()))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun syncProfessionRecipes(): ResponseEntity<AdminJob> =
        ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(adminProfessionSyncService.syncProfessionRecipes(requestedBy()))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getActiveProfessionSyncJob(): ResponseEntity<AdminJob> =
        ResponseEntity.ok(adminJobService.getActiveProfessionSyncJob())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun importProfessionTalentTrees(body: ProfessionTalentTreeImportRequest): ResponseEntity<Unit> {
        professionTalentTreeImportService.import(objectMapper.valueToTree(body))
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun inspectProfessionTalentTreeLua(file: MultipartFile): ResponseEntity<AuctionHelperTalentTreeLuaImportResult> =
        ResponseEntity.ok(
            (if (file.size > MAX_AUCTION_HELPER_IMPORT_BYTES) {
                professionTalentTreeLuaImportService.oversizedResult()
            } else {
                professionTalentTreeLuaImportService.inspect(file.bytes)
            }).toApiResult(),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun inspectAuctionHelperSavedVariables(
        files: Array<MultipartFile>,
    ): ResponseEntity<AuctionHelperSavedVariablesInspection> =
        ResponseEntity.ok(auctionHelperSavedVariablesInspectionService.inspect(files.toList()).toApiResult())

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminJob(id: Long): ResponseEntity<AdminJob> =
        ResponseEntity.ok(adminJobService.getJob(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun searchAdminItems(
        query: String?,
        locale: String?,
        hasBase: Boolean?,
        hasOverride: Boolean?,
        itemClassId: Int?,
        itemSubclassId: Int?,
        expansionId: Int?,
        hasRecipe: Boolean?,
        page: Int,
        pageSize: Int,
    ): ResponseEntity<AdminItemPage> =
        ResponseEntity.ok(
            adminItemService.searchItems(
                query,
                locale,
                hasBase,
                hasOverride,
                itemClassId,
                itemSubclassId,
                expansionId,
                hasRecipe,
                page,
                pageSize,
            ),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminItem(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): ResponseEntity<AdminItem1> =
        ResponseEntity.ok(adminItemService.getItem(id, locale, includeBase, includeOverride))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun searchAdminRecipes(
        query: String?,
        locale: String?,
        professionId: Int?,
        hasOverride: Boolean?,
        itemClassId: Int?,
        itemSubclassId: Int?,
        expansionId: Int?,
        associatedItemId: Int?,
        associationType: String?,
        page: Int,
        pageSize: Int,
    ): ResponseEntity<AdminRecipePage> =
        ResponseEntity.ok(
            adminRecipeService.searchRecipes(
                query,
                locale,
                professionId,
                hasOverride,
                itemClassId,
                itemSubclassId,
                expansionId,
                associatedItemId,
                associationType,
                page,
                pageSize,
            ),
        )

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun getAdminRecipe(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): ResponseEntity<AdminRecipe1> =
        ResponseEntity.ok(adminRecipeService.getRecipe(id, locale, includeBase, includeOverride))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminRecipeOverride(
        id: Int,
        body: AdminRecipeOverrideRequest,
    ): ResponseEntity<AdminRecipe1> = ResponseEntity.ok(adminRecipeService.upsertOverride(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteAdminRecipeOverride(id: Int): ResponseEntity<Unit> {
        adminRecipeService.deleteOverride(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun compareAdminRecipeWithApi(id: Int): ResponseEntity<AdminRecipeCompareResponse> =
        ResponseEntity.ok(adminRecipeService.compareWithApi(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun bulkUpsertAdminRecipeOverrides(
        body: AdminRecipeBulkOverrideRequest,
    ): ResponseEntity<List<AdminRecipe1>> =
        ResponseEntity.ok(adminRecipeService.bulkUpsertOverrides(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminItemOverride(
        id: Int,
        body: AdminItemOverrideRequest,
    ): ResponseEntity<AdminItem1> = ResponseEntity.ok(adminItemService.upsertOverride(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun upsertAdminItemRecipeAssociation(
        id: Int,
        recipeId: Int,
        body: AdminRecipeAssociationRequest,
    ): ResponseEntity<AdminItem1> = ResponseEntity.ok(adminItemService.upsertRecipeAssociation(id, recipeId, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteAdminItemOverride(id: Int): ResponseEntity<Unit> {
        adminItemService.deleteOverride(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createAdminItem(body: AdminItemCreateRequest): ResponseEntity<AdminItem1> =
        ResponseEntity.ok(adminItemService.createOverrideOnly(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun compareAdminItemWithApi(id: Int): ResponseEntity<AdminItemCompareResponse> =
        ResponseEntity.ok(adminItemService.compareWithApi(id))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun bulkUpsertAdminItemOverrides(body: AdminItemBulkOverrideRequest): ResponseEntity<List<AdminItem1>> =
        ResponseEntity.ok(adminItemService.bulkUpsertOverrides(body))

    // TODO: Need a paginated response - Update openApi as well
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listUsers(): ResponseEntity<List<User>> = ResponseEntity.ok(userService.getUsers())

    private fun requestedBy(): String? = SecurityContextHolder.getContext().authentication?.name
}

private fun net.jonasmf.auctionengine.domain.profession.AuctionHelperTalentTreeLuaImportResult.toApiResult() =
    AuctionHelperTalentTreeLuaImportResult(
        imported = imported,
        contentHash = contentHash,
        importedAt = importedAt.atOffset(java.time.ZoneOffset.UTC),
        charactersFound = charactersFound,
        professionsFound = professionsFound,
        recipesFound = recipesFound,
        diagnostics =
            diagnostics.map { diagnostic ->
                AuctionHelperTalentTreeLuaImportResultDiagnosticsInner(
                    code = AuctionHelperTalentTreeLuaImportResultDiagnosticsInner.Code.valueOf(diagnostic.code.name),
                    detail = diagnostic.detail,
                )
            },
    )

private fun net.jonasmf.auctionengine.domain.profession.AuctionHelperSavedVariablesInspection.toApiResult() =
    AuctionHelperSavedVariablesInspection(
        imported = imported,
        inspectedAt = inspectedAt.atOffset(java.time.ZoneOffset.UTC),
        sources = sources.map { source ->
            AuctionHelperSavedVariablesInspectionSourcesInner(
                fileName = AuctionHelperSavedVariablesInspectionSourcesInner.FileName.forValue(source.fileName),
                status = AuctionHelperSavedVariablesInspectionSourcesInner.Status.valueOf(source.status.name),
                contentHash = source.contentHash,
            )
        },
        charactersFound = charactersFound,
        professionsFound = professionsFound,
        recipesFound = recipesFound,
        diagnostics = diagnostics.map { diagnostic ->
            AuctionHelperTalentTreeLuaImportResultDiagnosticsInner(
                code = AuctionHelperTalentTreeLuaImportResultDiagnosticsInner.Code.valueOf(diagnostic.code.name),
                detail = diagnostic.detail,
            )
        },
        talentExport = talentExport?.let { export ->
            AuctionHelperSavedVariablesInspectionTalentExport(
                format = export.format,
                decodedBytes = export.decodedBytes,
                validTalentScope = export.validTalentScope,
                scope = export.scope,
                module = export.module,
                characterKey = export.characterKey,
                professionIdentifier = export.professionIdentifier,
            )
        },
    )

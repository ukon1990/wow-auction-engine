package net.jonasmf.auctionengine.admin.profession

import net.jonasmf.auctionengine.admin.AdminJobService
import net.jonasmf.auctionengine.admin.requestedBy
import net.jonasmf.auctionengine.config.JsonMappers
import net.jonasmf.auctionengine.generated.api.AdminProfessionApi
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionInspection
import net.jonasmf.auctionengine.generated.model.ProfessionTalentTreeImportRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminProfessionController(
    private val adminJobService: AdminJobService,
    private val adminProfessionSyncService: AdminProfessionSyncService,
    private val professionTalentTreeImportService: ProfessionTalentTreeImportService,
    private val normalizedAuctionHelperProfessionInspectionService: NormalizedAuctionHelperProfessionInspectionService,
) : AdminProfessionApi {
    private val objectMapper = JsonMappers.storage

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
    override suspend fun inspectNormalizedAuctionHelperProfessionData(
        body: NormalizedAuctionHelperProfessionData,
    ): ResponseEntity<NormalizedAuctionHelperProfessionInspection> =
        ResponseEntity.ok(normalizedAuctionHelperProfessionInspectionService.inspect(body, requestedBy() ?: "admin"))
}

package net.jonasmf.auctionengine.admin.expansion

import net.jonasmf.auctionengine.admin.requestedBy
import net.jonasmf.auctionengine.generated.api.AdminExpansionApi
import net.jonasmf.auctionengine.generated.model.AdminExpansion
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.AdminJob
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminExpansionController(
    private val adminExpansionService: AdminExpansionService,
) : AdminExpansionApi {
    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansions(locale: String?): ResponseEntity<List<AdminExpansion>> =
        ResponseEntity.ok(adminExpansionService.listExpansions(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansion(body: AdminExpansionRequest): ResponseEntity<AdminExpansion> =
        ResponseEntity.ok(adminExpansionService.createExpansion(body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun updateExpansion(
        id: Int,
        body: AdminExpansionRequest,
    ): ResponseEntity<AdminExpansion> = ResponseEntity.ok(adminExpansionService.updateExpansion(id, body))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun deleteExpansion(id: Int): ResponseEntity<Unit> {
        adminExpansionService.deleteExpansion(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun listExpansionRanges(locale: String?): ResponseEntity<List<AdminExpansionItemRange>> =
        ResponseEntity.ok(adminExpansionService.listRanges(locale))

    @PreAuthorize("hasAuthority('admin')")
    override suspend fun createExpansionRange(
        body: AdminExpansionItemRangeRequest,
    ): ResponseEntity<AdminExpansionItemRange> = ResponseEntity.ok(adminExpansionService.createRange(body))

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
}

package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.generated.api.AdminApi
import net.jonasmf.auctionengine.generated.model.AuctionHouse
import net.jonasmf.auctionengine.generated.model.AuctionHousePage
import net.jonasmf.auctionengine.generated.model.AuctionMarketSort
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.mapper.realm.toDto
import net.jonasmf.auctionengine.service.AuctionHouseService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

@PreAuthorize("hasAuthority('admin')")
@RestController
class AdminAuctionHouseController(
    private val auctionHouseService: AuctionHouseService,
) : AdminApi {
    override suspend fun getAuctionHouseById(id: Int): ResponseEntity<AuctionHouse> {
        val auctionHouse =
            auctionHouseService.findById(id)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(auctionHouse.toDto())
    }

    override suspend fun listAuctionHouses(): ResponseEntity<AuctionHousePage> {
        val auctionHouses =
            auctionHouseService
                .findAll()
                .map { it.toDto() }
                .sortedBy { it.connectedRealmId }
        return ResponseEntity.ok(
            AuctionHousePage(
                items = auctionHouses,
                page =
                    PageMetadata(
                        page = 0,
                        pageSize = auctionHouses.size,
                        totalItems = auctionHouses.size.toLong(),
                        totalPages = if (auctionHouses.isEmpty()) 0 else 1,
                    ),
                sort =
                    AuctionMarketSort(
                        sortBy = "connectedRealmId",
                        sortDirection = AuctionMarketSort.SortDirection.ASC,
                    ),
            ),
        )
    }
}

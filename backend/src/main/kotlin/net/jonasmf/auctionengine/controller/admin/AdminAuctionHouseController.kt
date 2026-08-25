package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.constant.admin.AdminAuctionHousePageSortBy
import net.jonasmf.auctionengine.constant.admin.toDomain
import net.jonasmf.auctionengine.generated.api.AdminAuctionHouseApi
import net.jonasmf.auctionengine.generated.model.AuctionHouse
import net.jonasmf.auctionengine.generated.model.AuctionHousePage
import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.generated.model.Sorting
import net.jonasmf.auctionengine.mapper.realm.toDto
import net.jonasmf.auctionengine.service.admin.AdminAuctionHouseService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import kotlin.math.ceil

@PreAuthorize("hasAuthority('admin')")
@RestController
class AdminAuctionHouseController(
    private val service: AdminAuctionHouseService,
) : AdminAuctionHouseApi {
    override suspend fun getAuctionHouseById(id: Int): ResponseEntity<AuctionHouse> {
        val auctionHouse =
            service.getByid(id)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(auctionHouse.toDto())
    }

    override suspend fun searchAuctionHouses(
        page: Int,
        pageSize: Int,
        sortBy: AuctionHousePageSortBy?,
        sortDirection: String,
    ): ResponseEntity<AuctionHousePage> {
        val pageSize = 20
        val sortDirectionMapped = Sorting.SortDirection.forValue(sortDirection)
        val (items, totalRows) =
            service.search(
                page = 1,
                limit = pageSize,
                sortBy = sortBy.toDomain() ?: AdminAuctionHousePageSortBy.NAME,
                sortDirection = sortDirectionMapped,
            )
        val totalNumberOfPages = ceil(totalRows.toDouble() / pageSize.toDouble()).toInt()

        return ResponseEntity.ok(
            AuctionHousePage(
                items = items.map { it.toDto() },
                page =
                    PageMetadata(
                        page = 0,
                        pageSize = pageSize,
                        totalItems = totalRows,
                        totalPages = totalNumberOfPages,
                    ),
                sort =
                    Sorting(
                        sortBy = sortBy,
                        sortDirection = sortDirectionMapped,
                    ),
            ),
        )
    }
}

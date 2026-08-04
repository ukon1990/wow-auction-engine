package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.generated.model.AuctionHousePage
import net.jonasmf.auctionengine.generated.model.AuctionMarketSort
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.mapper.realm.toDomain
import net.jonasmf.auctionengine.repository.rds.admin.AdminAuctionHouseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdminAuctionHouseService(
    val repository: AdminAuctionHouseRepository,
) {
    val logger = LoggerFactory.getLogger(AdminAuctionHouseService::class.java)

    fun getByid(id: Int): AuctionHouse? {
        val house = repository.findById(id).orElse(null)
        return house
    }

    fun search(
        page: Int,
        limit: Int,
        sortBy: String,
        sortDirection: AuctionMarketSort.SortDirection,
    ): AuctionHousePage {
        val houses = repository.findAll()
        return AuctionHousePage(
            items = houses.map { it.toDomain() },
            page = PageMetadata(
                page = page,
                pageSize = limit,
                totalItems = houses.,
                totalPages = TODO(),
            ),
            sort = sortDirection,
        )
    }
}

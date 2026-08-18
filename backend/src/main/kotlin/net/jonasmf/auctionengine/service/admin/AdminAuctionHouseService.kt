package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.dbo.rds.admin.toAuctionHouseDomain
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.generated.model.Sorting
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
        return house.toDomain()
    }

    fun search(
        page: Int,
        limit: Int,
        sortBy: String,
        sortDirection: Sorting.SortDirection,
    ): Pair<List<AuctionHouse>, Long> {
        val rows =
            repository.findAllWithQuery(
                offset = 0,
                pageSize = page,
                orderBy = "", // TODO: liten sjekk
            )
        val totalRows =
            rows.firstOrNull().let {
                it?.pageTotalItems ?: 0L
            }
        val items =
            rows
                .groupBy { row -> row.auctionHouseId }
                .map { (_, rows) -> rows.first().toAuctionHouseDomain(rows) }

        return Pair(items, totalRows)
    }
}

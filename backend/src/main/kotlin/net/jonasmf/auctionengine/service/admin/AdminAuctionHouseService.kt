package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.constant.admin.AdminAuctionHousePageSortBy
import net.jonasmf.auctionengine.dbo.rds.admin.toAuctionHouseDomain
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.generated.model.Sorting
import net.jonasmf.auctionengine.generated.model.UpdateAuctionHouse
import net.jonasmf.auctionengine.mapper.realm.toDomain
import net.jonasmf.auctionengine.repository.rds.admin.AdminAuctionHouseJdbcRepository
import net.jonasmf.auctionengine.repository.rds.admin.AdminAuctionHouseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdminAuctionHouseService(
    val repository: AdminAuctionHouseRepository,
    val jdbcRepository: AdminAuctionHouseJdbcRepository,
) {
    val logger = LoggerFactory.getLogger(AdminAuctionHouseService::class.java)

    fun getById(id: Int): AuctionHouse? {
        val house = repository.findByConnectedRealmId(id)
        return house?.toDomain()
    }

    fun update(
        id: Int,
        auctionHouse: UpdateAuctionHouse,
    ): AuctionHouse? {
        jdbcRepository.update(
            id,
            auctionHouse,
        )
        return getById(id)
    }

    fun search(
        page: Int,
        limit: Int,
        sortBy: AdminAuctionHousePageSortBy,
        sortDirection: Sorting.SortDirection,
    ): Pair<List<AuctionHouse>, Long> {
        val rows =
            repository.findAllWithQuery(
                offset = (page) * limit,
                pageSize = limit,
                orderBy = sortBy.value,
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

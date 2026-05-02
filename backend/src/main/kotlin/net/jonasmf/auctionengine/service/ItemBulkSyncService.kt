package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemBulkSyncService(
    private val itemJdbcRepository: ItemJdbcRepository,
) {
    @Transactional
    fun syncItems(items: List<Item>): ItemPersistenceSummary = itemJdbcRepository.syncItems(items)
}

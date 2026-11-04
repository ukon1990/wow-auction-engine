package net.jonasmf.auctionengine.item

import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.item.repository.ItemJdbcRepository
import net.jonasmf.auctionengine.item.repository.ItemPersistenceSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemBulkSyncService(
    private val itemJdbcRepository: ItemJdbcRepository,
) {
    @Transactional
    fun syncItems(items: List<Item>): ItemPersistenceSummary = itemJdbcRepository.syncItems(items)
}

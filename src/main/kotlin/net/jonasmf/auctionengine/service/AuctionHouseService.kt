package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.springframework.stereotype.Service

@Service
class AuctionHouseService(
    val repository: AuctionHouseDynamoRepository,
) {
    fun createIfMissing(connectedRealm: ConnectedRealm) {
        val auctionHouse = repository.findById(connectedRealm.id)
        if (!auctionHouse.isEmpty) return
        val newAuctionHouse =
            AuctionHouseDynamo(
                id = connectedRealm.id,
                region =
                    connectedRealm.realms
                        .firstOrNull()
                        ?.region
                        ?.name
                        .orEmpty(),
                realmSlugs = connectedRealm.realms.joinToString(",") { it.slug },
            )
        repository.save(newAuctionHouse)
    }

    fun getReadyForUpdate(region: Region) = repository.findAllByRegion(region)
}

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
        if (connectedRealm.realms.isEmpty()) return
        val newAuctionHouse =
            AuctionHouseDynamo(
                id = connectedRealm.id,
                region = connectedRealm.realms.first().region.type,
                realmSlugs = connectedRealm.realms.joinToString(",") { it.slug },
            )
        repository.save(newAuctionHouse)
    }

    fun findAllByRegion(region: Region) = repository.findAllByRegion(region)
    fun getReadyForUpdate(region: Region) = repository.findReadyForUpdateByRegion(region)
}

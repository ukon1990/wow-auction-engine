package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.mapper.realm.toDomain
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import kotlin.time.toJavaInstant
import net.jonasmf.auctionengine.domain.realm.AuctionHouse as AuctionHouseDomain
import net.jonasmf.auctionengine.domain.realm.Realm as RealmDomain
import net.jonasmf.auctionengine.repository.AuctionHouseRepository as AuctionHouseStateRepository

@Repository
class AuctionHouseStateRepositoryImpl(
    private val auctionHouseRepository: AuctionHouseRepository,
    private val connectedRealmRepository: ConnectedRealmRepository,
) : AuctionHouseStateRepository {
    override fun findById(id: Int?): AuctionHouseDomain? {
        if (id == null) return null

        val entity =
            auctionHouseRepository.findByConnectedId(id).orElse(null)
                ?: connectedRealmRepository.findById(id).orElse(null)?.auctionHouse
                ?: return null
        return entity.toDomain()
    }

    override fun findAllByRegion(region: Region): List<AuctionHouseDomain> =
        auctionHouseRepository.findAllByRegion(region).map { it.toDomain() }

    override fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDomain> =
        auctionHouseRepository
            .findAllByRegionAndNextUpdateLessThanEqualOrderByNextUpdateAsc(
                region,
                java.time.Instant.now(),
                PageRequest.of(0, 50),
            ).map { it.toDomain() }

    override fun save(auctionHouse: AuctionHouseDomain): AuctionHouseDomain {
        requireNotNull(auctionHouse.id) { "AuctionHouse.id must not be null when saving to MariaDB" }
        requireNotNull(auctionHouse.lastModified) { "AuctionHouse.lastModified cannot be null when saving to MariaDB" }

        val connectedId = auctionHouse.connectedId.takeIf { it != 0 } ?: auctionHouse.id!!
        val connectedRealm = connectedRealmRepository.findById(connectedId).orElse(null)
        val existing = auctionHouseRepository.findByConnectedId(connectedId).orElse(null)
        val entity =
            existing?.applyDomain(auctionHouse)
                ?: (
                    connectedRealm?.auctionHouse?.applyDomain(auctionHouse)
                        ?: AuctionHouse().applyDomain(auctionHouse)
                )

        val saved =
            if (connectedRealm != null && connectedRealm.auctionHouse.id != entity.id) {
                connectedRealm.auctionHouse = entity
                connectedRealmRepository.save(connectedRealm).auctionHouse
            } else {
                auctionHouseRepository.save(entity)
            }

        return saved.toDomain()
    }

    private fun resolveRealms(connectedId: Int): List<RealmDomain> {
        val connectedRealm = connectedRealmRepository.findById(connectedId).orElse(null) ?: return emptyList()
        return connectedRealm.realms.map { it -> it.toDomain() }
    }
}

private fun AuctionHouse.applyDomain(domain: AuctionHouseDomain): AuctionHouse {
    val connectedIdValue = domain.connectedId.takeIf { it != 0 } ?: domain.id ?: 0
    val regionValue =
        domain.region.takeIf { connectedIdValue < 0 || it != Region.Europe || domain.id == null }
            ?: region

    return apply {
        connectedId = connectedIdValue
        region = regionValue
        autoUpdate = domain.autoUpdate
        avgDelay = domain.avgDelay
        gameBuild = domain.gameBuild
        highestDelay = domain.highestDelay
        lastDailyPriceUpdate = domain.lastDailyPriceUpdate?.toJavaInstant()
        lastHistoryDeleteEvent = domain.lastHistoryDeleteEvent?.toJavaInstant()
        lastHistoryDeleteEventDaily = domain.lastHistoryDeleteEventDaily?.toJavaInstant()
        lastModified = domain.lastModified?.toJavaInstant()
        lastRequested = domain.lastRequested?.toJavaInstant()
        lowestDelay = domain.lowestDelay
        nextUpdate = domain.nextUpdate?.toJavaInstant()
        updateAttempts = domain.updateAttempts
    }
}

package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmUpsertRow
import net.jonasmf.auctionengine.repository.rds.RealmSyncRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ConnectedRealmBulkSyncService(
    private val connectedRealmJdbcRepository: ConnectedRealmJdbcRepository,
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val auctionHouseRepository: AuctionHouseRepository,
) {
    private val log = LoggerFactory.getLogger(ConnectedRealmBulkSyncService::class.java)

    @Transactional
    fun sync(connectedRealms: List<ConnectedRealm>): List<ConnectedRealm> {
        if (connectedRealms.isEmpty()) return emptyList()

        val uniqueConnectedRealms = connectedRealms.distinctBy { it.id }
        val connectedRealmIds = uniqueConnectedRealms.map { it.id }
        val existingAuctionHouseIds =
            connectedRealmJdbcRepository.findAuctionHouseIdsByConnectedRealmIds(connectedRealmIds).toMutableMap()
        val auctionHousesByConnectedId =
            auctionHouseRepository.findAllByConnectedIdIn(connectedRealmIds).associateBy { it.connectedId }
        val auctionHousesById =
            auctionHouseRepository.findAllById(existingAuctionHouseIds.values).associateBy { requireNotNull(it.id) }

        uniqueConnectedRealms.forEach { connectedRealm ->
            val existingAuctionHouse =
                auctionHousesByConnectedId[connectedRealm.id]
                    ?: existingAuctionHouseIds[connectedRealm.id]?.let(auctionHousesById::get)

            val savedAuctionHouse =
                if (existingAuctionHouse != null) {
                    auctionHouseRepository.save(normalizeAuctionHouse(existingAuctionHouse, connectedRealm))
                } else {
                    val createdAuctionHouse = normalizeAuctionHouse(connectedRealm.auctionHouse, connectedRealm)
                    auctionHouseRepository.saveAndFlush(createdAuctionHouse).also {
                        log.info("Created auction house {} for new connected realm {}", it.id, connectedRealm.id)
                    }
                }

            existingAuctionHouseIds[connectedRealm.id] =
                requireNotNull(savedAuctionHouse.id) {
                    "Auction house ID must be generated for connected realm ${connectedRealm.id}"
                }
        }

        connectedRealmJdbcRepository.upsertConnectedRealms(
            uniqueConnectedRealms.map { connectedRealm ->
                ConnectedRealmUpsertRow(
                    id = connectedRealm.id,
                    auctionHouseId =
                        existingAuctionHouseIds[connectedRealm.id]
                            ?: error("Missing auction house mapping for connected realm ${connectedRealm.id}"),
                )
            },
        )

        val realmRows =
            uniqueConnectedRealms.flatMap { connectedRealm ->
                connectedRealm.realms.map { realm ->
                    RealmSyncRow(
                        connectedRealmId = connectedRealm.id,
                        realmId = realm.id,
                        regionId = requireNotNull(realm.region.id),
                        name = realm.name,
                        category = realm.category,
                        locale = realm.locale.ordinal,
                        timezone = realm.timezone,
                        gameBuild = realm.gameBuild.ordinal,
                        slug = realm.slug,
                    )
                }
            }

        connectedRealmJdbcRepository.replaceRealmsForConnectedRealms(connectedRealmIds, realmRows)

        val savedById = connectedRealmRepository.findAllById(connectedRealmIds).associateBy { it.id }
        return connectedRealmIds.mapNotNull(savedById::get)
    }

    private fun normalizeAuctionHouse(
        auctionHouse: AuctionHouse,
        connectedRealm: ConnectedRealm,
    ): AuctionHouse {
        val resolvedRegion =
            connectedRealm.realms
                .firstOrNull()
                ?.region
                ?.type ?: auctionHouse.region

        return auctionHouse.apply {
            connectedId = connectedRealm.id
            region = resolvedRegion
            lastModified = lastModified ?: Instant.EPOCH
            nextUpdate = nextUpdate ?: Instant.EPOCH
            lowestDelay = lowestDelay ?: 0L
            avgDelay = avgDelay ?: 60L
            highestDelay = highestDelay ?: 0L
            updateAttempts = updateAttempts ?: 0
        }
    }
}

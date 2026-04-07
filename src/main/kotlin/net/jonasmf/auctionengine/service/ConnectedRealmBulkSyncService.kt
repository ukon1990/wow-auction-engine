package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmUpsertRow
import net.jonasmf.auctionengine.repository.rds.RealmSyncRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

        uniqueConnectedRealms.forEach { connectedRealm ->
            if (existingAuctionHouseIds.containsKey(connectedRealm.id)) return@forEach

            val auctionHouseId =
                requireNotNull(auctionHouseRepository.saveAndFlush(connectedRealm.auctionHouse).id) {
                    "Auction house ID must be generated for connected realm ${connectedRealm.id}"
                }
            existingAuctionHouseIds[connectedRealm.id] = auctionHouseId
            log.info("Created auction house {} for new connected realm {}", auctionHouseId, connectedRealm.id)
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
}

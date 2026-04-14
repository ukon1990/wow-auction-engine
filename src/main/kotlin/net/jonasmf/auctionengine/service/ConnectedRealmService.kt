package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.integration.blizzard.BlizzardConnectedRealmApiClient
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import kotlin.collections.emptyList
import kotlin.text.get

@Service
class ConnectedRealmService(
    private val properties: BlizzardApiProperties,
    private val blizzardConnectedRealmApiClient: BlizzardConnectedRealmApiClient,
    private val regionService: RegionService,
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val regionRepository: RegionRepository,
    private val auctionHouseService: AuctionHouseService,
    private val connectedRealmBulkSyncService: ConnectedRealmBulkSyncService,
) {
    val log: Logger = LoggerFactory.getLogger(ConnectedRealmService::class.java)
    private val seededAt: Instant = Instant.EPOCH

    @Scheduled(
        fixedDelayString = "PT1H",
        initialDelayString = "\${app.scheduling.initial-delay:PT30S}",
    )
    fun updateRealms() {
        // return // TODO: FInd a better way to determine when to run this or not. It's annoying to run every time
        regionService.ensureRegionsExist()
        val configuredRegions = properties.configuredRegions
        val communityIds = configuredRegions.map(::communityIdForRegion)
        val communityRealms = mutableListOf<ConnectedRealm>()
        communityIds.forEach { id ->
            val connectedDBO = connectedRealmRepository.findById(id).orElse(null)
            if (connectedDBO == null) {
                log.info("Connected Realm with id $id not found. Creating it")

                // Check if region exists first
                val regionOptional = regionRepository.findById(id * -1)
                if (regionOptional.isEmpty) {
                    log.error("Region with id ${id * -1} not found. Cannot create ConnectedRealm for id $id")
                    return@forEach
                }

                val region = regionOptional.get()
                val connectedRealm =
                    ConnectedRealm(
                        id = id,
                        realms =
                            mutableListOf(
                                Realm(
                                    id = id,
                                    name = "Community",
                                    slug = "community",
                                    locale = Locale.EN_GB,
                                    region = region,
                                    timezone = "UTC",
                                    category = "Community",
                                    gameBuild = GameBuildVersion.RETAIL,
                                ),
                            ),
                        auctionHouse =
                            AuctionHouse(
                                connectedId = id,
                                region = region.type,
                                lastModified = seededAt,
                                lastRequested = null,
                                nextUpdate = seededAt,
                                lowestDelay = 60,
                                avgDelay = 60,
                                highestDelay = 60,
                                tsmFile = null,
                                statsFile = null,
                                auctionFile = null,
                                updateAttempts = 0,
                            ),
                    )
                communityRealms.add(connectedRealmRepository.save(connectedRealm))
                log.info("Successfully created ConnectedRealm with id $id")
            } else {
                communityRealms.add(connectedDBO)
                log.debug("Connected Realm with id $id already exists")
            }
        }

        log.info("Checking for updates for configured regions: {}", configuredRegions)
        val connectedRealms =
            listOf(
                communityRealms,
                configuredRegions.flatMap { region ->
                    getAndUpdate(region).block() ?: emptyList<ConnectedRealm>()
                },
            ).flatten()

        for (realm in connectedRealms) {
            auctionHouseService.createIfMissing(realm)
        }
    }

    private fun communityIdForRegion(region: Region): Int =
        when (region) {
            Region.NorthAmerica -> -1
            Region.Europe -> -2
            Region.Korea -> -3
            Region.Taiwan -> -4
        }

    fun updateLatestDump(
        connectedId: Int,
        lastModified: Instant,
    ) {
        val connectedRealm = connectedRealmRepository.findById(connectedId)
        if (connectedRealm.isPresent) {
            val house = connectedRealm.get().auctionHouse
            house.lastModified = lastModified
            house.nextUpdate = lastModified.plusSeconds(house.avgDelay * 60)
            connectedRealmRepository.save(connectedRealm.get())
        }
    }

    fun getById(id: Int): ConnectedRealm? = connectedRealmRepository.findById(id).orElse(null)

    fun getAllForRegion(region: Region) = connectedRealmRepository.findAllByRegion(region)

    fun getAndUpdate(region: Region): Mono<List<ConnectedRealm>> {
        log.info("Fetching connected realms for region: ${region.name}")

        return blizzardConnectedRealmApiClient
            .getConnectedRealmIndex(region)
            .doOnError { error ->
                log.error("Failed to fetch connected realm index for region ${region.name}: $error")
            }.flatMapMany { index ->
                log.info(
                    "Successfully fetched connected realm index for region ${region.name}. Found ${index.connectedRealms.size} realms.",
                )

                Flux
                    .fromIterable(index.connectedRealms)
                    .flatMap({ realm -> fetchRealm(realm, region) }, 1)
            }.collectList()
            .flatMap { connectedRealms ->
                Mono
                    .fromCallable { connectedRealmBulkSyncService.sync(connectedRealms) }
                    .subscribeOn(Schedulers.boundedElastic())
            }.doOnSuccess {
                log.info("Successfully processed all connected realms for region: ${region.name}")
            }.doOnError { error ->
                log.error("Error occurred while processing connected realms for region ${region.name}: $error")
            }
    }

    private fun fetchRealm(
        realm: Href,
        region: Region,
    ): Mono<ConnectedRealm> =
        blizzardConnectedRealmApiClient
            .getConnectedRealm(realm)
            .doOnSubscribe {
                log.debug("Fetching realm data for ${realm.href}")
            }.doOnError { error ->
                log.error("Failed to fetch realm data for ${realm.href}: $error")
            }.map { connectedRealmDTO ->
                log.debug("Successfully fetched realm data for ${realm.href}")
                val payloadRegions = connectedRealmDTO.realms.mapNotNull { it.payloadRegion() }.toSet()
                if (payloadRegions.isNotEmpty() && payloadRegions != setOf(region)) {
                    log.warn(
                        "Realm {} returned payload regions {} while fetched under {}. Using fetch context as source of truth.",
                        connectedRealmDTO.id,
                        payloadRegions,
                        region,
                    )
                }
                connectedRealmDTO.toDBO(region)
            }
}

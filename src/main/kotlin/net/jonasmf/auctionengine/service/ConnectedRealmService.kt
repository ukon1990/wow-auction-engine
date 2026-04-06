package net.jonasmf.auctionengine.service

import NameSpace
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.realm.ConnectedRealmDTO
import net.jonasmf.auctionengine.dto.realm.ConnectedRealmIndex
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import net.jonasmf.auctionengine.utility.determineBaseUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.ZonedDateTime
import kotlin.collections.emptyList
import kotlin.text.get

@Service
class ConnectedRealmService(
    private val properties: BlizzardApiProperties,
    webClientWithAuth: WebClient,
    private val authService: AuthService,
    private val regionService: RegionService,
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val regionRepository: RegionRepository,
    private val auctionHouseService: AuctionHouseService,
) {
    private val webClient: WebClient = webClientWithAuth
    private val basePath = "connected-realm/index"
    val log: Logger = LoggerFactory.getLogger(ConnectedRealmService::class.java)

    @Scheduled(fixedDelayString = "PT1H", initialDelay = 3_000)
    fun updateRealms() {
        regionService.ensureRegionsExist()
        val communityIds = listOf(-1, -2, -3, -4)
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
                                lastModified = null,
                                lastRequested = null,
                                nextUpdate = ZonedDateTime.now(),
                                lowestDelay = 60,
                                averageDelay = 60,
                                highestDelay = 60,
                                tsmFile = null,
                                statsFile = null,
                                auctionFile = null,
                                failedAttempts = 0,
                            ),
                    )
                communityRealms.add(connectedRealmRepository.save(connectedRealm))
                log.info("Successfully created ConnectedRealm with id $id")
            } else {
                communityRealms.add(connectedDBO)
                log.debug("Connected Realm with id $id already exists")
            }
        }

        log.info("Checking for updates...")
        val connectedRealms =
            listOf(
                communityRealms,
                getAndUpdate(Region.NorthAmerica).block() ?: emptyList<ConnectedRealm>(),
                getAndUpdate(Region.Europe).block() ?: emptyList<ConnectedRealm>(),
                getAndUpdate(Region.Korea).block() ?: emptyList<ConnectedRealm>(),
                getAndUpdate(Region.Taiwan).block() ?: emptyList<ConnectedRealm>(),
            ).flatten()

        for (realm in connectedRealms) {
            auctionHouseService.createIfMissing(realm)
        }
    }

    fun updateLatestDump(
        connectedId: Int,
        lastModified: ZonedDateTime,
    ) {
        val connectedRealm = connectedRealmRepository.findById(connectedId)
        if (connectedRealm.isPresent) {
            val house = connectedRealm.get().auctionHouse
            house.lastModified = lastModified
            house.nextUpdate = lastModified.plusMinutes(house.averageDelay)
            connectedRealmRepository.save(connectedRealm.get())
        }
    }

    fun getById(id: Int): ConnectedRealm? = connectedRealmRepository.findById(id).orElse(null)

    fun getAllForRegion(region: Region) = connectedRealmRepository.findAllByRegion(region)

    fun getAndUpdate(region: Region): Mono<List<ConnectedRealm>> {
        log.info("Fetching connected realms for region: ${region.name}")

        return getConnectedRealmIndex(region)
            .doOnError { error ->
                log.error("Failed to fetch connected realm index for region ${region.name}: $error")
            }.flatMapMany { index ->
                log.info(
                    "Successfully fetched connected realm index for region ${region.name}. Found ${index.connectedRealms.size} realms.",
                )

                Flux
                    .fromIterable(index.connectedRealms)
                    .flatMap(
                        { realm ->
                            processRealm(realm, region)
                        },
                        1,
                    ) // Process one realm at a time to avoid overwhelming the API
            }.collectList()
            .doOnSuccess {
                log.info("Successfully processed all connected realms for region: ${region.name}")
            }.doOnError { error ->
                log.error("Error occurred while processing connected realms for region ${region.name}: $error")
            }
    }

    private fun processRealm(
        realm: Href,
        region: Region,
    ): Mono<ConnectedRealm> =
        getRealm(realm)
            .doOnSubscribe {
                log.debug("Fetching realm data for ${realm.href}")
            }.doOnError { error ->
                log.error("Failed to fetch realm data for ${realm.href}: $error")
            }.map { connectedRealmDTO ->
                log.debug("Successfully fetched realm data for ${realm.href}")
                connectedRealmDTO.toDBO()
            }.flatMap { connectedDBO ->
                checkAndSaveRealm(connectedDBO, region)
            }

    private fun checkAndSaveRealm(
        connectedDBO: ConnectedRealm,
        region: Region,
    ): Mono<ConnectedRealm> =
        Mono
            .fromCallable {
                val existingRealm = connectedRealmRepository.findById(connectedDBO.id)

                if (existingRealm.isPresent) {
                    existingRealm.get()
                } else {
                    log.info("Saving new connected realm ${connectedDBO.id} for region ${region.name}")
                    connectedRealmRepository.save(connectedDBO)
                }
            }.subscribeOn(Schedulers.boundedElastic())

    private fun getConnectedRealmIndex(region: Region): Mono<ConnectedRealmIndex> {
        log.info("Fetching connected realm index...")
        val uri =
            UriComponentsBuilder
                .fromUriString(determineBaseUrl(region, properties))
                .path(basePath)
                .queryParam("namespace", NameSpace.getDynamicForRegion(region).value)
                .queryParam("locale", "en_GB")
                .toUriString()

        return webClient
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono(ConnectedRealmIndex::class.java)
    }

    private fun getRealm(url: Href): Mono<ConnectedRealmDTO> =
        webClient
            .get()
            .uri(url.href)
            .retrieve()
            .bodyToMono(ConnectedRealmDTO::class.java)
}

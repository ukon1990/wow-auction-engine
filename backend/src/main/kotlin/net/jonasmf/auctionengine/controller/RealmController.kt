package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.RealmApi
import net.jonasmf.auctionengine.generated.model.Realm
import net.jonasmf.auctionengine.generated.model.RealmDetail
import net.jonasmf.auctionengine.service.RealmQueryService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RestController
class RealmController(
    private val realmQueryService: RealmQueryService,
) : RealmApi {
    private val logger = LoggerFactory.getLogger(RealmController::class.java)

    override suspend fun listRealms(): ResponseEntity<List<Realm>> {
        val totalStart = System.nanoTime()
        val result = realmQueryService.listAllRealms()
        val totalDuration = (System.nanoTime() - totalStart).toDuration(DurationUnit.NANOSECONDS)

        logger.info(
            "Realm catalog request completed in {}ms (query={}ms mapSort={}ms total={}ms)",
            totalDuration.inWholeMilliseconds,
            result.queryDuration.inWholeMilliseconds,
            result.mapSortDuration.inWholeMilliseconds,
            totalDuration.inWholeMilliseconds,
        )

        return ResponseEntity
            .ok()
            .header(
                "Server-Timing",
                result.serverTimingHeader(totalDuration),
            ).body(result.realms)
    }

    override suspend fun getRealm(
        region: String,
        slug: String,
    ): ResponseEntity<RealmDetail> {
        val detail = realmQueryService.getRealmDetail(region, slug) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }
}

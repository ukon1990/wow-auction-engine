package net.jonasmf.auctionengine.auction

import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.realm.repository.RealmCatalogJdbcRepository
import net.jonasmf.auctionengine.auction.defaultAuctionHouseZone
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class AuctionSnapshot(
    val connectedRealmId: Int,
    val date: LocalDate,
    val hour: Int,
    val timestamp: OffsetDateTime,
)

data class AuctionContext(
    val region: Region,
    val locale: Locale,
    val localeColumnSuffix: String,
    val selectedRealmTimezone: String,
    val selectedSnapshot: AuctionSnapshot,
    val commoditySnapshot: AuctionSnapshot,
    val selectedAuctionHouseLastModified: Instant,
    val commodityAuctionHouseLastModified: Instant,
)

@Service
class AuctionContextService(
    private val realmCatalogJdbcRepository: RealmCatalogJdbcRepository,
) {
    fun resolve(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
    ): AuctionContext {
        val region =
            runCatching { Region.fromString(regionCode) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.message, it) }
        val detail =
            realmCatalogJdbcRepository.findRealmDetailRow(region, realmSlug)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found: $regionCode/$realmSlug")

        val realmLocale =
            Locale.getAllValues()[detail.locale]
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unknown realm locale string: ${detail.locale}",
                )
        val locale = localeOverride?.takeIf { it.isNotBlank() }?.parseLocale() ?: realmLocale

        val selectedLastModified =
            detail.lastModified
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Auction house for connected realm ${detail.connectedRealmId} has no last modified timestamp",
                )
        val commodityLastModified =
            detail.commodityLastModified
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Commodity auction house for $regionCode has no last modified timestamp",
                )

        return AuctionContext(
            region = region,
            locale = locale,
            localeColumnSuffix = locale.columnSuffix,
            selectedRealmTimezone = detail.timezone,
            selectedSnapshot =
                snapshotFrom(
                    lastModified = selectedLastModified,
                    connectedRealmId = detail.connectedRealmId,
                    preferredTimezone = detail.timezone,
                ),
            commoditySnapshot =
                snapshotFrom(
                    lastModified = commodityLastModified,
                    connectedRealmId = detail.commodityConnectedRealmId,
                    preferredTimezone = null,
                ),
            selectedAuctionHouseLastModified = selectedLastModified,
            commodityAuctionHouseLastModified = commodityLastModified,
        )
    }

    private fun snapshotFrom(
        lastModified: Instant,
        connectedRealmId: Int,
        preferredTimezone: String?,
    ): AuctionSnapshot {
        val zone = preferredTimezone?.toZoneIdOrNull() ?: defaultAuctionHouseZone
        val local = lastModified.atZone(zone)
        return AuctionSnapshot(
            connectedRealmId = connectedRealmId,
            date = local.toLocalDate(),
            hour = local.hour,
            timestamp = local.toOffsetDateTime(),
        )
    }

    private fun String.toZoneIdOrNull(): ZoneId? = runCatching { ZoneId.of(this) }.getOrNull()

    private fun String.parseLocale(): Locale =
        runCatching { Locale.getAllValues().getValue(this) }
            .recoverCatching { Locale.fromCompactString(this) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported locale: $this", it) }

    private val Locale.columnSuffix: String
        get() = value.lowercase()
}

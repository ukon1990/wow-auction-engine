package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.generated.model.AuctionHouseStatus
import net.jonasmf.auctionengine.generated.model.RealmDetail
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import net.jonasmf.auctionengine.dbo.rds.realm.Realm as RealmEntity
import net.jonasmf.auctionengine.generated.model.Realm as RealmDto

@Service
class RealmQueryService(
    private val connectedRealmRepository: ConnectedRealmRepository,
) {
    fun listAllRealms(): List<RealmDto> =
        connectedRealmRepository
            .findAll()
            .asSequence()
            .filter { it.id >= 0 }
            .flatMap { connected -> connected.realms.asSequence().map { realm -> realm.toDto() } }
            .sortedWith(compareBy({ it.region.value }, { it.name }))
            .toList()

    fun getRealmDetail(
        regionCode: String,
        slug: String,
    ): RealmDetail? {
        val region = runCatching { Region.fromString(regionCode) }.getOrNull() ?: return null
        val matchingRealmAndConnected =
            connectedRealmRepository
                .findAllByRegion(region)
                .firstNotNullOfOrNull { connected ->
                    connected.realms
                        .firstOrNull { it.slug.equals(slug, ignoreCase = true) }
                        ?.let { realm -> connected to realm }
                } ?: return null

        val (connectedRealm, realm) = matchingRealmAndConnected
        val community =
            connectedRealmRepository
                .findById(CommunityRealms.idFor(region))
                .orElse(null) ?: return null

        return RealmDetail(
            realm = realm.toDto(),
            auctionHouse = connectedRealm.toAuctionHouseStatus(),
            community = community.toAuctionHouseStatus(),
        )
    }

    private fun RealmEntity.toDto(): RealmDto =
        RealmDto(
            region = region.type.toDtoEnum(),
            name = name,
            category = category,
            slug = slug,
            locale = locale.value,
            timezone = timezone,
        )

    private fun ConnectedRealm.toAuctionHouseStatus(): AuctionHouseStatus =
        AuctionHouseStatus(
            connectedRealmId = id,
            lastDailyPriceUpdate = auctionHouse.lastDailyPriceUpdate.toOffsetDateTime(),
            lastModified = auctionHouse.lastModified.toOffsetDateTime(),
            nextUpdate = auctionHouse.nextUpdate.toOffsetDateTime(),
        )

    private fun Region.toDtoEnum(): RealmDto.Region =
        when (this) {
            Region.NorthAmerica -> RealmDto.Region.US
            Region.Europe -> RealmDto.Region.EU
            Region.Korea -> RealmDto.Region.KR
            Region.Taiwan -> RealmDto.Region.TW
        }

    private fun Instant?.toOffsetDateTime(): OffsetDateTime? = this?.atOffset(ZoneOffset.UTC)
}

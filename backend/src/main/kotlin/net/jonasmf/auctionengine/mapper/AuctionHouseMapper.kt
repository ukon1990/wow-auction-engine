package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.domain.realm.Region
import net.jonasmf.auctionengine.mapper.realm.toDto
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as Dbo
import net.jonasmf.auctionengine.domain.realm.AuctionHouse as Domain
import net.jonasmf.auctionengine.generated.model.AuctionHouse as Dto

fun Dbo.toDomain() =
    Domain(
        id = id,
    )

fun Domain.toDto() =
    Dto(
        id = id,
        connectedRealmId = connectedId,
        region = region.toString(),
        realms = realms.map { it.toDto() },
    )

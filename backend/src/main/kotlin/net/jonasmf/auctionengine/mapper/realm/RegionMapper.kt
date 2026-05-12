package net.jonasmf.auctionengine.mapper.realm

import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.domain.realm.Region as RegionDomain

fun RegionDBO.toDomain() =
    RegionDomain(
        id = id,
        name = name,
        type = type,
    )

fun RegionDomain.toDbo() =
    RegionDBO(
        id = id,
        name = name,
        type = type,
    )

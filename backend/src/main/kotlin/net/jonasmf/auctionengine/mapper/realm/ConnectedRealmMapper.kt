package net.jonasmf.auctionengine.mapper.realm

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm as ConnectedRealmDbo
import net.jonasmf.auctionengine.domain.realm.ConnectedRealm as ConnectedRealmDomain

fun ConnectedRealmDbo.toDomain() =
    ConnectedRealmDomain(
        id = id,
        auctionHouse = auctionHouse.toDomain(),
        realms = realms.map { realm -> realm.toDomain() }.toMutableList(),
    )

fun ConnectedRealmDomain.toDbo() =
    ConnectedRealmDbo(
        id = id,
        auctionHouse = auctionHouse.toDbo(),
        realms = realms.map { realm -> realm.toDbo() }.toMutableList(),
    )

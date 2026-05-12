package net.jonasmf.auctionengine.mapper.realm

import net.jonasmf.auctionengine.dbo.rds.realm.Realm as RealmDbo
import net.jonasmf.auctionengine.domain.realm.Realm as RealmDomain

fun RealmDomain.toDbo() =
    RealmDbo(
        id = id,
        locale = locale,
        name = name,
        slug = slug,
        category = category,
        gameBuild = gameBuild,
        region = region.toDbo(),
        timezone = timezone,
    )

fun RealmDbo.toDomain() =
    RealmDomain(
        id = id,
        locale = locale,
        name = name,
        slug = slug,
        category = category,
        gameBuild = gameBuild,
        region = region.toDomain(),
        timezone = timezone,
    )

package net.jonasmf.auctionengine.domain.realm

class ConnectedRealm(
    var id: Int,
    var auctionHouse: AuctionHouse,
    var realms: MutableList<Realm> = mutableListOf(),

)

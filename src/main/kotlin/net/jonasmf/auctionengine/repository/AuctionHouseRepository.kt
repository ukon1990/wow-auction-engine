package net.jonasmf.auctionengine.repository

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.AuctionHouse
import java.util.Optional

interface AuctionHouseRepository {
    fun findById(id: Int?): Optional<AuctionHouse>

    fun findAllByRegion(region: Region): List<AuctionHouse>

    fun findReadyForUpdateByRegion(region: Region): List<AuctionHouse>

    fun save(auctionHouse: AuctionHouse): AuctionHouse
}

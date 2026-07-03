package net.jonasmf.auctionengine.repository

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import java.time.OffsetDateTime

interface AuctionHouseRepository {
    fun findById(id: Int?): AuctionHouse?

    fun findAllByRegion(region: Region): List<AuctionHouse>

    fun findReadyForUpdateByRegion(region: Region): List<AuctionHouse>

    fun findAllByLastHistoryDeleteEventBefore(hourlyTTL: OffsetDateTime): List<AuctionHouse>

    fun findAllByLastHistoryDeleteEventDailyBefore(dailyTTL: OffsetDateTime): List<AuctionHouse>

    fun save(auctionHouse: AuctionHouse): AuctionHouse
}

package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.repository.dynamodb.AuctionHouseDynamoRepository
import org.junit.Assert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AuctionHouseServiceTest(
    val repository: AuctionHouseDynamoRepository,
    val auctionHouseService: AuctionHouseService,
) {
    val auctionHouses = listOf<AuctionHouseDynamo>(
        AuctionHouseDynamo(
            id = 1,
            autoUpdate = true,
            region = Region.Europe,
        ),
        AuctionHouseDynamo(
            id = 2,
            autoUpdate = true,
            region = Region.NorthAmerica,
        ),
    )

    fun beforeAll() {
        auctionHouses.forEach {repository.save(it) }
    }

    @Nested
    inner class GetReadyForUpdate() {
        @Test
        fun canGetAuctionHouses() {
            val result = auctionHouseService.getReadyForUpdate(Region.Europe)
            Assert.assertEquals(auctionHouses.size, 1)
        }
    }
}

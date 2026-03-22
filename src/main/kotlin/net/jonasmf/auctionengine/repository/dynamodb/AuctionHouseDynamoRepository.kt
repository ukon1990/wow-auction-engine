package net.jonasmf.auctionengine.repository.dynamodb

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import org.springframework.data.repository.CrudRepository

interface AuctionHouseDynamoRepository : CrudRepository<AuctionHouseDynamo, Int?>

package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionItemModifier
import org.springframework.data.jpa.repository.JpaRepository

interface AuctionItemModifierRepository : JpaRepository<AuctionItemModifier, Long>
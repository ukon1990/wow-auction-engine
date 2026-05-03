package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region

/**
 * Single source of truth for the synthetic "community" connected realm ids used to model
 * the regional aggregate auction house. Each region owns one community connected realm whose
 * id is the negative of the region row id (1..4) so it cannot collide with any real Blizzard
 * connected realm id.
 */
object CommunityRealms {
    fun idFor(region: Region): Int =
        when (region) {
            Region.NorthAmerica -> -1
            Region.Europe -> -2
            Region.Korea -> -3
            Region.Taiwan -> -4
        }
}

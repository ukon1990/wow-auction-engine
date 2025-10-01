package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.constant.Region

fun getBucketName(region: Region?): String =
    when (region) {
        Region.NorthAmerica -> "wah-data-us"
        Region.Europe -> "wah-data-eu"
        Region.Korea -> "wah-data-kr"
        Region.Taiwan -> "wah-data-tw"
        else -> "wah-data"
    }

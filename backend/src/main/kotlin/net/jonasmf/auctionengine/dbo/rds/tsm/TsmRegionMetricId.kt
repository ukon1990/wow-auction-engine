package net.jonasmf.auctionengine.dbo.rds.tsm

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.constant.TsmSubjectType
import java.io.Serializable

data class TsmRegionMetricId(
    val region: Region = Region.Europe,
    val subjectType: TsmSubjectType = TsmSubjectType.ITEM,
    val subjectId: Int = 0,
) : Serializable

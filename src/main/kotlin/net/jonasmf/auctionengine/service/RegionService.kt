package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.repository.rds.RegionRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RegionService(
    private val regionRepository: RegionRepository,
) {
    val log: Logger = LoggerFactory.getLogger(RegionService::class.java)

    private fun mapIdToValues(id: Int): Pair<String, Region> {
        return when (id) {
            1 -> Pair("US", Region.NorthAmerica)
            2 -> Pair("EU", Region.Europe)
            3 -> Pair("KR", Region.Korea)
            4 -> Pair("TW", Region.Taiwan)
            else -> Pair("Unknown", Region.Europe)
        }
    }

    fun ensureRegionsExist() {
        val regionIds = listOf<Int>(1, 2, 3, 4)
        regionIds.forEach { id ->
            val regionOptional = regionRepository.findById(id)
            if (regionOptional.isEmpty) {
                log.info("Region with id $id not found. Creating it")
                val regionPair = mapIdToValues(id)
                val region =
                    RegionDBO(
                        id = id,
                        name = regionPair.first,
                        type = regionPair.second,
                    )
                regionRepository.save(region)
            }
        }
    }
}

package net.jonasmf.auctionengine.service

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

    fun ensureRegionsExist() {
        val regionIds = listOf<Int>(1, 2, 3, 4)
        regionIds.forEach { id ->
            val regionOptional = regionRepository.findById(id)
            if (regionOptional.isEmpty) {
                log.info("Region with id $id not found. Creating it")
                val region =
                    RegionDBO(
                        id = id,
                        name =
                            when (id) {
                                1 -> "US"
                                2 -> "EU"
                                3 -> "KR"
                                4 -> "TW"
                                else -> "Unknown"
                            },
                    )
                regionRepository.save(region)
            }
        }
    }
}

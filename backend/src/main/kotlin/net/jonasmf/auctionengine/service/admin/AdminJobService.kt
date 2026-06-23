package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

object AdminJobDomain {
    const val ITEM = "item"
    const val RECIPE = "recipe"
    const val PROFESSION = "profession"
    const val MEDIA = "media"
    const val SYSTEM = "system"
}

object AdminJobOperations {
    const val APPLY_EXPANSION_RANGES = "apply-expansion-ranges"
    const val FETCH_EXPANSION_RANGE_ITEMS = "fetch-expansion-range-items"
}

@Service
class AdminJobService(
    private val adminJobRepository: AdminJobRepository,
) {
    fun getJob(id: Long): AdminJob =
        adminJobRepository.findJob(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Admin job not found: $id")
}

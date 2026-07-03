package net.jonasmf.auctionengine.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.auction-cleanup")
data class AuctionCleanupProperties(
    val enabled: Boolean = true,
    val dryRun: Boolean = false,
    val optimizeEnabled: Boolean = true,
    val batchSize: Int = 10_000,
    val hourlyRetention: Duration = Duration.ofDays(14),
    val dailyRetention: Duration = Duration.ofDays(120),
    val deletedAuctionRetention: Duration = Duration.ofDays(7),
) {
    init {
        require(batchSize > 0) { "Cleanup batch size must be positive" }
        require(!hourlyRetention.isNegative && !hourlyRetention.isZero) { "Hourly retention must be positive" }
        require(!dailyRetention.isNegative && !dailyRetention.isZero) { "Daily retention must be positive" }
        require(!deletedAuctionRetention.isNegative && !deletedAuctionRetention.isZero) {
            "Deleted auction retention must be positive"
        }
    }
}

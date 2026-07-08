package net.jonasmf.auctionengine.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.scheduling.deleted-auction-cleanup")
data class DeletedAuctionCleanupProperties(
    val enabled: Boolean = true,
    val hourlyRetention: Duration = Duration.ofDays(14),
    val dailyRetention: Duration = Duration.ofDays(120),
    val priceRetention: Duration = Duration.ofDays(7),
) {
    init {
        require(!hourlyRetention.isNegative && !hourlyRetention.isZero) {
            "Hourly cleanup retention must be positive"
        }
        require(!dailyRetention.isNegative && !dailyRetention.isZero) {
            "Daily cleanup retention must be positive"
        }
        require(!priceRetention.isNegative && !priceRetention.isZero) {
            "Price cleanup retention must be positive"
        }
    }
}

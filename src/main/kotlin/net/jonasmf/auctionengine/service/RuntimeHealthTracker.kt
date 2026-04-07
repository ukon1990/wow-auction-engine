package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RuntimeHealthSnapshot(
    val healthy: Boolean,
    val reason: String? = null,
)

@Component
class RuntimeHealthTracker(
    @Value("\${app.health.stuck-update-threshold:PT20M}")
    private val stuckUpdateThreshold: Duration = Duration.ofMinutes(20),
) {
    private val updateBatchRunning = AtomicBoolean(false)
    private val lastProgressAt = AtomicLong(System.currentTimeMillis())
    private val currentStage = AtomicReference("idle")
    private val currentRegion = AtomicReference<String?>(null)
    private val currentRealmId = AtomicReference<Int?>(null)

    fun markUpdateBatchStarted(regions: Collection<Region>) {
        updateBatchRunning.set(true)
        currentStage.set("batch-started")
        currentRegion.set(regions.joinToString(","))
        currentRealmId.set(null)
        lastProgressAt.set(System.currentTimeMillis())
    }

    fun markUpdateBatchProgress(
        stage: String,
        region: Region? = null,
        connectedRealmId: Int? = null,
    ) {
        currentStage.set(stage)
        currentRegion.set(region?.name ?: currentRegion.get())
        currentRealmId.set(connectedRealmId)
        lastProgressAt.set(System.currentTimeMillis())
    }

    fun markUpdateBatchCompleted() {
        updateBatchRunning.set(false)
        currentStage.set("idle")
        currentRegion.set(null)
        currentRealmId.set(null)
        lastProgressAt.set(System.currentTimeMillis())
    }

    fun markUpdateBatchFailed(stage: String) {
        updateBatchRunning.set(false)
        currentStage.set("failed:$stage")
        lastProgressAt.set(System.currentTimeMillis())
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): RuntimeHealthSnapshot {
        if (!updateBatchRunning.get()) {
            return RuntimeHealthSnapshot(healthy = true)
        }

        val stalledForMillis = nowMillis - lastProgressAt.get()
        if (stalledForMillis <= stuckUpdateThreshold.toMillis()) {
            return RuntimeHealthSnapshot(healthy = true)
        }

        val reason =
            buildString {
                append("Update batch stalled for ")
                append(Duration.ofMillis(stalledForMillis))
                append(" at stage ")
                append(currentStage.get())
                currentRegion.get()?.let {
                    append(" region=")
                    append(it)
                }
                currentRealmId.get()?.let {
                    append(" realm=")
                    append(it)
                }
            }

        return RuntimeHealthSnapshot(
            healthy = false,
            reason = reason,
        )
    }
}

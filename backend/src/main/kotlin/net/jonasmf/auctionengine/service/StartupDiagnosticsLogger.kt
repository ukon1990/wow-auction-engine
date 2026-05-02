package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.utility.JvmRuntimeDiagnostics
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class StartupDiagnosticsLogger {
    private val logger = LoggerFactory.getLogger(StartupDiagnosticsLogger::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun logStartupMemory() {
        logger.info("Application ready {}", JvmRuntimeDiagnostics.snapshot())
    }
}

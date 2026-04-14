package net.jonasmf.auctionengine.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.DefaultApplicationArguments
import reactor.core.publisher.Mono

class ConnectedRealmStartupSyncTest {
    @Test
    fun `run triggers connected realm update on startup`() {
        val authService = mockk<AuthService>()
        val connectedRealmService = mockk<ConnectedRealmService>()
        every { authService.ensureToken() } returns Mono.just("token")
        every { connectedRealmService.updateRealms() } returns Unit

        ConnectedRealmStartupSync(authService, connectedRealmService).run(DefaultApplicationArguments())

        verify(exactly = 1) { authService.ensureToken() }
        verify(exactly = 1) { connectedRealmService.updateRealms() }
    }

    @Test
    fun `run logs and continues when startup sync fails`() {
        val authService = mockk<AuthService>()
        val connectedRealmService = mockk<ConnectedRealmService>()
        val listAppender = attachAppender()
        every { authService.ensureToken() } throws RuntimeException("boom")

        try {
            ConnectedRealmStartupSync(authService, connectedRealmService).run(DefaultApplicationArguments())

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            assertTrue(
                messages.any {
                    it.contains("Connected realm startup synchronization failed. Continuing application startup.")
                },
            )
            verify(exactly = 1) { authService.ensureToken() }
            verify(exactly = 0) { connectedRealmService.updateRealms() }
        } finally {
            detachAppender(listAppender)
        }
    }

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(ConnectedRealmStartupSync::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(ConnectedRealmStartupSync::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}

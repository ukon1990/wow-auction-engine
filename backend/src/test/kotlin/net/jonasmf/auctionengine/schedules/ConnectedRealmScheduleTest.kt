package net.jonasmf.auctionengine.schedules

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.service.AuthService
import net.jonasmf.auctionengine.service.ConnectedRealmService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.DefaultApplicationArguments
import reactor.core.publisher.Mono

class ConnectedRealmScheduleTest {
    @Test
    fun `run triggers connected realm update on startup`() {
        val authService = mockk<AuthService>()
        val connectedRealmService = mockk<ConnectedRealmService>()
        every { authService.ensureToken() } returns Mono.just("token")
        every { connectedRealmService.updateRealms() } returns Unit

        ConnectedRealmSchedule(authService, connectedRealmService, mockk(relaxed = true))
            .run(DefaultApplicationArguments())

        verify(exactly = 1) { authService.ensureToken() }
        verify(exactly = 1) { connectedRealmService.updateRealms() }
    }

    @Test
    fun `run logs and continues when startup sync fails`() {
        val authService = mockk<AuthService>()
        val connectedRealmService = mockk<ConnectedRealmService>()
        val listAppender = attachAppender()
        every { authService.ensureToken() } throws RuntimeException("failure")

        try {
            ConnectedRealmSchedule(authService, connectedRealmService, mockk(relaxed = true))
                .run(DefaultApplicationArguments())

            val messages = listAppender.list.map(ILoggingEvent::getFormattedMessage)
            Assertions.assertTrue(
                messages.any {
                    it.contains("Connected realm startup synchronization failed.")
                },
            )
            verify(exactly = 1) { authService.ensureToken() }
            verify(exactly = 0) { connectedRealmService.updateRealms() }
        } finally {
            detachAppender(listAppender)
        }
    }

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(ConnectedRealmSchedule::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(ConnectedRealmSchedule::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}

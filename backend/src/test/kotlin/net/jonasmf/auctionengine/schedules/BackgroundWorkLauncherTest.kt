package net.jonasmf.auctionengine.schedules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BackgroundWorkLauncherTest {
    @Test
    fun `launchSingleFlight offloads work and skips a concurrent run`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val launcher = BackgroundWorkLauncher(scope)
        val running = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        try {
            assertTrue(
                launcher.launchSingleFlight(running, "test-task") {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                },
            )
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertFalse(launcher.launchSingleFlight(running, "test-task") {})

            release.countDown()
            awaitChildren(scope)

            assertFalse(running.get())
        } finally {
            release.countDown()
            scope.coroutineContext[Job]?.cancel()
            dispatcher.close()
        }
    }

    @Test
    fun `launchSingleFlight clears guard after failure`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val launcher = BackgroundWorkLauncher(scope)
        val running = AtomicBoolean(false)
        val started = CountDownLatch(1)

        try {
            assertTrue(
                launcher.launchSingleFlight(running, "failing-task") {
                    started.countDown()
                    throw IllegalStateException("boom")
                },
            )
            assertTrue(started.await(5, TimeUnit.SECONDS))
            awaitChildren(scope)

            assertFalse(running.get())
            assertTrue(launcher.launchSingleFlight(running, "failing-task") {})
            awaitChildren(scope)
        } finally {
            scope.coroutineContext[Job]?.cancel()
            dispatcher.close()
        }
    }

    private fun awaitChildren(scope: CoroutineScope) {
        runBlocking {
            scope.coroutineContext[Job]
                ?.children
                ?.toList()
                ?.joinAll()
        }
    }
}

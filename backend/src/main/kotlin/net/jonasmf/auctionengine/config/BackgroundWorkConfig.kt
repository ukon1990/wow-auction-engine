package net.jonasmf.auctionengine.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.io.Closeable
import java.util.concurrent.Executor

@Configuration(proxyBeanMethods = false)
class BackgroundWorkConfig {
    @Bean
    fun backgroundWorkExecutor(
        @Value("\${app.background-work.pool-size:4}") poolSize: Int,
        @Value("\${app.background-work.thread-name-prefix:background-work-}") threadNamePrefix: String,
        @Value("\${app.background-work.await-shutdown-seconds:60}") awaitShutdownSeconds: Int,
    ): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = poolSize
            maxPoolSize = poolSize
            setThreadNamePrefix(threadNamePrefix)
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(awaitShutdownSeconds)
        }

    @Bean(destroyMethod = "close")
    fun backgroundWorkScope(
        @Qualifier("backgroundWorkExecutor") executor: Executor,
    ): CoroutineScope = CloseableCoroutineScope(executor)
}

private class CloseableCoroutineScope(
    executor: Executor,
) : CoroutineScope,
    Closeable {
    override val coroutineContext = SupervisorJob() + executor.asCoroutineDispatcher()

    override fun close() {
        cancel()
    }
}

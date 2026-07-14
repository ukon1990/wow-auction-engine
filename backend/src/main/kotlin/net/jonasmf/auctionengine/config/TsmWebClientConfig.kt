package net.jonasmf.auctionengine.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

private const val TSM_PUBLIC_DATA_MAX_IN_MEMORY_BYTES = 64 * 1024 * 1024

@Configuration
class TsmWebClientConfig {
    @Bean
    fun tsmPublicDataWebClient(): WebClient =
        WebClient
            .builder()
            .codecs { codecs ->
                codecs.defaultCodecs().maxInMemorySize(TSM_PUBLIC_DATA_MAX_IN_MEMORY_BYTES)
            }.build()
}

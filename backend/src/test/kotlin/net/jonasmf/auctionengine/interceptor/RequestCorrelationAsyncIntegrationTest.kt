package net.jonasmf.auctionengine.interceptor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.jonasmf.auctionengine.config.MdcContextPropagationConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.context.annotation.Profile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

class RequestCorrelationAsyncIntegrationTest {
    private val contextPropagation = MdcContextPropagationConfig()

    @BeforeEach
    fun enableContextPropagation() {
        contextPropagation.registerMdcPropagation()
    }

    @AfterEach
    fun restoreContextPropagation() {
        contextPropagation.restoreContextPropagation()
        MDC.clear()
    }

    @Test
    fun `propagates identifiers across suspend off-thread work and into WebClient`() {
        val correlationId = "0198c728-8f4b-7ccb-9953-d23cc1976587"
        val clientSessionId = "0198c728-a27c-7286-a24e-230ea6be5045"
        val requestThread = Thread.currentThread().name
        val observedMdc = AtomicReference<Map<String, String>>()
        val workerThread = AtomicReference<String>()
        val outboundRequest = AtomicReference<ClientRequest>()
        val webClient = capturingWebClient(outboundRequest)
        val mockMvc = asyncMockMvc(observedMdc, workerThread, webClient)

        val initialResult =
            mockMvc
                .perform(
                    get("/async-correlation")
                        .header(CORRELATION_ID_HEADER, correlationId)
                        .header(CLIENT_SESSION_ID_HEADER, clientSessionId),
                ).andExpect(request().asyncStarted())
                .andReturn()

        mockMvc
            .perform(asyncDispatch(initialResult))
            .andExpect(status().isNoContent)
            .andExpect(header().string(CORRELATION_ID_HEADER, correlationId))
            .andExpect(header().string(CLIENT_SESSION_ID_HEADER, clientSessionId))

        assertThat(workerThread.get()).isNotEqualTo(requestThread)
        assertThat(observedMdc.get())
            .containsEntry(CORRELATION_ID_MDC_KEY, correlationId)
            .containsEntry(CLIENT_SESSION_ID_MDC_KEY, clientSessionId)
        assertThat(outboundRequest.get().headers().getFirst(CORRELATION_ID_HEADER)).isEqualTo(correlationId)
        assertThat(outboundRequest.get().headers().getFirst(CLIENT_SESSION_ID_HEADER)).isEqualTo(clientSessionId)
        assertThat(MDC.getCopyOfContextMap()).isNull()
    }

    private fun asyncMockMvc(
        observedMdc: AtomicReference<Map<String, String>>,
        workerThread: AtomicReference<String>,
        webClient: WebClient,
    ): MockMvc {
        val builder = MockMvcBuilders.standaloneSetup(AsyncCorrelationTestController(observedMdc, workerThread, webClient))
        return builder
            .addFilters<StandaloneMockMvcBuilder>(RequestCorrelationFilter())
            .build()
    }

    private fun capturingWebClient(outboundRequest: AtomicReference<ClientRequest>): WebClient =
        WebClient
            .builder()
            .filter(correlationHeadersFilter())
            .exchangeFunction { request ->
                outboundRequest.set(request)
                Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build())
            }.build()
}

@RestController
@Profile("request-correlation-standalone-test")
private class AsyncCorrelationTestController(
    private val observedMdc: AtomicReference<Map<String, String>>,
    private val workerThread: AtomicReference<String>,
    private val webClient: WebClient,
) {
    @GetMapping("/async-correlation")
    suspend fun correlation(): ResponseEntity<Unit> =
        withContext(Dispatchers.Default) {
            delay(10)
            workerThread.set(Thread.currentThread().name)
            observedMdc.set(MDC.getCopyOfContextMap())
            webClient.get().uri("https://example.invalid/test").retrieve().toBodilessEntity().block()
            ResponseEntity.noContent().build()
        }
}

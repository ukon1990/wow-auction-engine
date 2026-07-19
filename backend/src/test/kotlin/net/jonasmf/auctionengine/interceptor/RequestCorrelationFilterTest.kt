package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

class RequestCorrelationFilterTest {
    private val filter = RequestCorrelationFilter()

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `accepts and echoes valid identifiers while making them available to the chain`() {
        val correlationId = "30bc30e8-ace7-47dc-b94a-34df764d6c13"
        val clientSessionId = "293cd74d-e790-4390-b292-55f89a60722a"
        val request = MockHttpServletRequest("GET", "/any-route").apply {
            addHeader(CORRELATION_ID_HEADER, correlationId)
            addHeader(CLIENT_SESSION_ID_HEADER, clientSessionId)
        }
        val response = MockHttpServletResponse()
        val observedMdc = AtomicReference<Map<String, String>>()

        filter.doFilter(request, response, FilterChain { _, _ -> observedMdc.set(MDC.getCopyOfContextMap()) })

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(correlationId)
        assertThat(response.getHeader(CLIENT_SESSION_ID_HEADER)).isEqualTo(clientSessionId)
        assertThat(observedMdc.get())
            .containsEntry(CORRELATION_ID_MDC_KEY, correlationId)
            .containsEntry(CLIENT_SESSION_ID_MDC_KEY, clientSessionId)
        assertThat(MDC.getCopyOfContextMap()).isNull()
    }

    @Test
    fun `accepts a version 7 UUID at the backend boundary`() {
        val correlationId = "0198c728-8f4b-7ccb-9953-d23cc1976587"
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("GET", "/any-route").apply {
            addHeader(CORRELATION_ID_HEADER, correlationId)
        }

        filter.doFilter(request, response, FilterChain { _, _ ->
            assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isEqualTo(correlationId)
        })

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(correlationId)
    }

    @Test
    fun `generates a correlation id when the header is missing without inventing a session id`() {
        val response = MockHttpServletResponse()
        val observedCorrelationId = AtomicReference<String>()

        filter.doFilter(MockHttpServletRequest("GET", "/health"), response, FilterChain { _, _ ->
            observedCorrelationId.set(MDC.get(CORRELATION_ID_MDC_KEY))
        })

        assertThat(response.getHeader(CORRELATION_ID_HEADER))
            .isEqualTo(observedCorrelationId.get())
            .matches(CANONICAL_UUID_PATTERN)
        assertThat(response.getHeader(CLIENT_SESSION_ID_HEADER)).isNull()
    }

    @Test
    fun `replaces an invalid correlation id and drops an invalid session id`() {
        val request = MockHttpServletRequest("GET", "/any-route").apply {
            addHeader(CORRELATION_ID_HEADER, "forged\ncorrelation")
            addHeader(CLIENT_SESSION_ID_HEADER, "not-a-uuid")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ ->
            assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).matches(CANONICAL_UUID_PATTERN)
            assertThat(MDC.get(CLIENT_SESSION_ID_MDC_KEY)).isNull()
        })

        assertThat(response.getHeader(CORRELATION_ID_HEADER)).matches(CANONICAL_UUID_PATTERN)
        assertThat(response.getHeader(CLIENT_SESSION_ID_HEADER)).isNull()
    }

    @Test
    fun `clears stale request values and restores the complete previous MDC after failure`() {
        MDC.setContextMap(
            mapOf(
                CORRELATION_ID_MDC_KEY to "stale-correlation",
                CLIENT_SESSION_ID_MDC_KEY to "stale-session",
                "backgroundJob" to "job-7",
            ),
        )
        val previousMdc = MDC.getCopyOfContextMap()

        assertThatThrownBy {
            filter.doFilter(MockHttpServletRequest("GET", "/failure"), MockHttpServletResponse(), FilterChain { _, _ ->
                assertThat(MDC.get(CLIENT_SESSION_ID_MDC_KEY)).isNull()
                assertThat(MDC.get("backgroundJob")).isNull()
                throw IllegalStateException("expected")
            })
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previousMdc)
    }

    @Test
    fun `outbound filter propagates current identifiers without changing authorization`() {
        val correlationId = "30bc30e8-ace7-47dc-b94a-34df764d6c13"
        val clientSessionId = "293cd74d-e790-4390-b292-55f89a60722a"
        val capturedRequest = AtomicReference<ClientRequest>()
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId)
        MDC.put(CLIENT_SESSION_ID_MDC_KEY, clientSessionId)
        val client =
            WebClient
                .builder()
                .filter(correlationHeadersFilter())
                .exchangeFunction { request ->
                    capturedRequest.set(request)
                    Mono.just(ClientResponse.create(HttpStatus.OK).build())
                }.build()

        client.get().uri("https://example.invalid/test").header("Authorization", "Bearer token").retrieve().toBodilessEntity().block()

        assertThat(capturedRequest.get().headers().getFirst(CORRELATION_ID_HEADER)).isEqualTo(correlationId)
        assertThat(capturedRequest.get().headers().getFirst(CLIENT_SESSION_ID_HEADER)).isEqualTo(clientSessionId)
        assertThat(capturedRequest.get().headers().getFirst("Authorization")).isEqualTo("Bearer token")
    }

    @Test
    fun `outbound filter preserves explicitly supplied identifiers when MDC context is absent`() {
        val capturedRequest = AtomicReference<ClientRequest>()
        val explicitCorrelationId = "0198c728-8f4b-7ccb-9953-d23cc1976587"
        val explicitClientSessionId = "0198c728-a27c-7286-a24e-230ea6be5045"
        val client =
            WebClient
                .builder()
                .filter(correlationHeadersFilter())
                .exchangeFunction { request ->
                    capturedRequest.set(request)
                    Mono.just(ClientResponse.create(HttpStatus.OK).build())
                }.build()

        client
            .get()
            .uri("https://example.invalid/test")
            .header(CORRELATION_ID_HEADER, explicitCorrelationId)
            .header(CLIENT_SESSION_ID_HEADER, explicitClientSessionId)
            .retrieve()
            .toBodilessEntity()
            .block()

        assertThat(capturedRequest.get().headers().getFirst(CORRELATION_ID_HEADER)).isEqualTo(explicitCorrelationId)
        assertThat(capturedRequest.get().headers().getFirst(CLIENT_SESSION_ID_HEADER)).isEqualTo(explicitClientSessionId)
    }
}

private const val CANONICAL_UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"

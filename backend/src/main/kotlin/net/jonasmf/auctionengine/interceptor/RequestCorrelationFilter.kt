package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import java.util.UUID

const val CORRELATION_ID_HEADER = "X-Correlation-ID"
const val CLIENT_SESSION_ID_HEADER = "X-Client-Session-ID"
const val CORRELATION_ID_MDC_KEY = "correlationId"
const val CLIENT_SESSION_ID_MDC_KEY = "clientSessionId"

private val CANONICAL_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val previousMdc = MDC.getCopyOfContextMap()
        val correlationId = validatedUuid(request.getHeader(CORRELATION_ID_HEADER)) ?: UUID.randomUUID().toString()
        val clientSessionId = validatedUuid(request.getHeader(CLIENT_SESSION_ID_HEADER))

        try {
            MDC.clear()
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId)
            clientSessionId?.let { MDC.put(CLIENT_SESSION_ID_MDC_KEY, it) }
            response.setHeader(CORRELATION_ID_HEADER, correlationId)
            clientSessionId?.let { response.setHeader(CLIENT_SESSION_ID_HEADER, it) }
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
            previousMdc?.let(MDC::setContextMap)
        }
    }
}

fun correlationHeadersFilter(): ExchangeFilterFunction =
    ExchangeFilterFunction { request, next ->
        val correlationId = validatedUuid(MDC.get(CORRELATION_ID_MDC_KEY))
        val clientSessionId = validatedUuid(MDC.get(CLIENT_SESSION_ID_MDC_KEY))
        val propagatedRequest =
            ClientRequest
                .from(request)
                .headers { headers ->
                    correlationId?.let { headers.set(CORRELATION_ID_HEADER, it) }
                    clientSessionId?.let { headers.set(CLIENT_SESSION_ID_HEADER, it) }
                }.build()
        next.exchange(propagatedRequest)
    }

private fun validatedUuid(value: String?): String? =
    value
        ?.takeIf { it.length == UUID_STRING_LENGTH && CANONICAL_UUID.matches(it) }

private const val UUID_STRING_LENGTH = 36

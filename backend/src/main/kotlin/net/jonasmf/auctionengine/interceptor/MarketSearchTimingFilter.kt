package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class MarketSearchTimingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(MarketSearchTimingFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return path != "/auctions/search" && path != "/auctions/search/filters"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startNanos = System.nanoTime()

        try {
            filterChain.doFilter(request, response)
        } finally {
            val totalMs = elapsedMs(startNanos)
            log.info(
                "Market search request completed in {}ms (correlationId={} method={} uri={} status={})",
                totalMs,
                MDC.get(CORRELATION_ID_MDC_KEY) ?: "-",
                request.method,
                request.requestURI,
                response.status,
            )
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}

package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val NORMALIZED_IMPORT_PATH = "/admin/profession-talent-trees/inspect-normalized"
private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val REQUEST_ID_MDC_KEY = "requestId"

@Component
class NormalizedProfessionImportRequestFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(NormalizedProfessionImportRequestFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI.removePrefix(request.contextPath) != NORMALIZED_IMPORT_PATH

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val previousRequestId = MDC.get(REQUEST_ID_MDC_KEY)
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            log.info(
                "Normalized profession import request completed (method={} status={} contentLength={})",
                request.method,
                response.status,
                request.contentLengthLong,
            )
            if (previousRequestId == null) MDC.remove(REQUEST_ID_MDC_KEY) else MDC.put(REQUEST_ID_MDC_KEY, previousRequestId)
        }
    }

}

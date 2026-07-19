package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val NORMALIZED_IMPORT_PATH = "/admin/profession-talent-trees/inspect-normalized"

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
        try {
            filterChain.doFilter(request, response)
        } finally {
            log.info(
                "Normalized profession import request completed (method={} status={} contentLength={})",
                request.method,
                response.status,
                request.contentLengthLong,
            )
        }
    }
}

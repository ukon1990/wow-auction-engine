package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val NORMALIZED_IMPORT_PATH = "/admin/profession-talent-trees/inspect-normalized"
private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val REQUEST_ID_MDC_KEY = "requestId"

@Component
class NormalizedProfessionImportRequestFilter(
    @Value("\${app.import.normalized-profession-max-request-bytes:33554432}")
    private val maxRequestBytes: Int,
) : OncePerRequestFilter() {
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
            if (request.contentLengthLong > maxRequestBytes) {
                rejectOversized(response, request.contentLengthLong)
                return
            }
            val body = request.inputStream.readNBytes(maxRequestBytes + 1)
            if (body.size > maxRequestBytes) {
                rejectOversized(response, body.size.toLong())
                return
            }
            filterChain.doFilter(BufferedBodyRequest(request, body), response)
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

    private fun rejectOversized(
        response: HttpServletResponse,
        contentLength: Long,
    ) {
        log.warn("Normalized profession import request exceeds byte limit (contentLength={} maxBytes={})", contentLength, maxRequestBytes)
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Normalized profession import request exceeds the byte limit")
    }
}

private class BufferedBodyRequest(
    request: HttpServletRequest,
    private val body: ByteArray,
) : HttpServletRequestWrapper(request) {
    override fun getContentLength(): Int = body.size

    override fun getContentLengthLong(): Long = body.size.toLong()

    override fun getInputStream(): ServletInputStream = ByteArrayServletInputStream(body)

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
}

private class ByteArrayServletInputStream(body: ByteArray) : ServletInputStream() {
    private val input = ByteArrayInputStream(body)

    override fun read(): Int = input.read()

    override fun isFinished(): Boolean = input.available() == 0

    override fun isReady(): Boolean = true

    override fun setReadListener(readListener: ReadListener?) = Unit
}

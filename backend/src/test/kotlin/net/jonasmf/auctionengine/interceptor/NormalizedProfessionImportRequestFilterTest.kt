package net.jonasmf.auctionengine.interceptor

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class NormalizedProfessionImportRequestFilterTest {
    private val filter = NormalizedProfessionImportRequestFilter()

    @Test
    fun `allows a large request and preserves correlation id`() {
        val request = importRequest(ByteArray(64 * 1024 * 1024)).apply { addHeader("X-Request-Id", "request-123") }
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(response))
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123")
        assertThat(MDC.get("requestId")).isNull()
    }

    private fun importRequest(body: ByteArray) =
        MockHttpServletRequest("POST", "/admin/profession-talent-trees/inspect-normalized").apply {
            setContent(body)
            contentType = "application/json"
        }
}

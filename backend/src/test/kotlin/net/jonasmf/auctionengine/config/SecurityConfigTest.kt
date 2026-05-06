package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.controller.AdminController
import net.jonasmf.auctionengine.controller.HealthController
import net.jonasmf.auctionengine.service.RuntimeHealthSnapshot
import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(
    controllers = [
        AdminController::class,
        HealthController::class,
    ],
)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    ],
)
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var runtimeHealthTracker: RuntimeHealthTracker

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `health remains public`() {
        `when`(runtimeHealthTracker.snapshot(anyLong())).thenReturn(RuntimeHealthSnapshot(healthy = true))

        mockMvc.get("/health")
            .andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `admin session check rejects anonymous requests`() {
        mockMvc.get("/admin/session-check")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `admin session check accepts valid jwt authentication`() {
        mockMvc
            .get("/admin/session-check") {
                with(jwt())
            }.andExpect {
                status { isNoContent() }
            }
    }
}

package net.jonasmf.auctionengine

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class)
class HealthControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `health endpoint returns no content`() {
        mockMvc
            .get("/health")
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }
    }
}

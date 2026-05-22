package net.jonasmf.auctionengine.controller

import io.mockk.mockk
import net.jonasmf.auctionengine.service.admin.UserService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {
    private val userService: UserService = mockk<UserService>()
    private val controller = AdminController(userService)

    @Nested
    inner class ListUsers {
        @Autowired
        private lateinit var mockMvc: MockMvc

        private fun performAsync(result: MvcResult) = mockMvc.perform(asyncDispatch(result))

        @Test
        fun `should return a list of users is admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/users")
                            .contextPath("/api")
                            .with(jwt().authorities(SimpleGrantedAuthority("ADMIN"))),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 401 if not admin`() {
            val result =
                mockMvc
                    .perform(
                        get("/api/admin/users")
                            .contextPath("/api")
                            .with(jwt()),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isUnauthorized)
        }
    }
}

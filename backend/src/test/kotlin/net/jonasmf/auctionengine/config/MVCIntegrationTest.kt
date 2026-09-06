package net.jonasmf.auctionengine.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request

/**
 * A helper for writing integration tests for MVC(controllers).
 * It uses test containers for storage.
 *
 */
@AutoConfigureMockMvc
@ImportAutoConfiguration(
    ServletWebSecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class,
)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    ],
)
abstract class MVCIntegrationTest : IntegrationTestBase() {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @MockitoBean
    protected lateinit var jwtDecoder: JwtDecoder

    @Autowired
    protected lateinit var cognitoGroupsGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>>

    protected fun mvcGet(
        path: String,
        roles: List<String> = listOf(),
    ) = mockMvc
        .perform(
            get(path)
                .contextPath("/api")
                .with(
                    jwt()
                        .jwt { token -> token.claim("cognito:groups", roles) }
                        .authorities(cognitoGroupsGrantedAuthoritiesConverter),
                ),
        ).andExpect(request().asyncStarted())
        .andReturn()

    protected fun mvcPatch(
        path: String,
        body: Any? = null,
        roles: List<String> = listOf(),
    ) = mockMvc
        .perform(
            patch(path, body)
                .contextPath("/api")
                .with(
                    jwt()
                        .jwt { token -> token.claim("cognito:groups", roles) }
                        .authorities(cognitoGroupsGrantedAuthoritiesConverter),
                ),
        ).andExpect(request().asyncStarted())
        .andReturn()
}

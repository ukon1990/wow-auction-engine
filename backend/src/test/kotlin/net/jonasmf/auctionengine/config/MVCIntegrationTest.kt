package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.service.admin.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.convert.converter.Converter
import org.springframework.http.MediaType
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import tools.jackson.databind.ObjectMapper

/**
 * Base class for controller integration tests: real filter chain, services and Testcontainers storage.
 * The `mvc*` helpers send the request as [actor] (anonymous when null), complete async (suspend)
 * handlers and return [ResultActions], so a test is just `mvcGet(path, admin).andExpect(...)`.
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

    /** Cognito is an external boundary; mocked here so every MVC test shares one Spring context. */
    @MockitoBean
    protected lateinit var userService: UserService

    @Autowired
    protected lateinit var cognitoGroupsGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>>

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    /** A signed-in caller; [groups] are its Cognito groups. */
    protected data class Actor(
        val subject: String = "test-user",
        val groups: List<String> = emptyList(),
    )

    protected val user = Actor()
    protected val admin = Actor(subject = "test-admin", groups = listOf("admin"))

    protected fun mvcGet(
        path: String,
        actor: Actor? = user,
    ) = perform(get(path), actor)

    protected fun mvcPost(
        path: String,
        body: Any? = null,
        actor: Actor? = user,
    ) = perform(post(path), actor, body)

    protected fun mvcPut(
        path: String,
        body: Any? = null,
        actor: Actor? = user,
    ) = perform(put(path), actor, body)

    protected fun mvcPatch(
        path: String,
        body: Any? = null,
        actor: Actor? = user,
    ) = perform(patch(path), actor, body)

    protected fun mvcDelete(
        path: String,
        actor: Actor? = user,
    ) = perform(delete(path), actor)

    private fun perform(
        request: MockHttpServletRequestBuilder,
        actor: Actor?,
        body: Any? = null,
    ): ResultActions {
        request.contextPath("/api")
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        }
        if (actor != null) {
            request.with(
                jwt()
                    .jwt { token -> token.subject(actor.subject).claim("cognito:groups", actor.groups) }
                    .authorities(cognitoGroupsGrantedAuthoritiesConverter),
            )
        }
        val actions = mockMvc.perform(request)
        val result = actions.andReturn()
        return if (result.request.isAsyncStarted) mockMvc.perform(asyncDispatch(result)) else actions
    }
}

package net.jonasmf.auctionengine.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuerUri: String,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/admin/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }

        if (issuerUri.isNotBlank()) {
            http.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { }
            }
        }

        return http.build()
    }
}

package net.jonasmf.auctionengine.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuerUri: String,
) {
    @Bean
    fun cognitoGroupsGrantedAuthoritiesConverter(): Converter<Jwt, Collection<GrantedAuthority>> {
        val defaultAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

        return Converter { jwt ->
            val scopeAuthorities = defaultAuthoritiesConverter.convert(jwt) ?: emptyList()
            val groupAuthorities =
                jwt.getClaimAsStringList("cognito:groups")
                    ?.map { group -> SimpleGrantedAuthority(group) }
                    ?: emptyList()

            scopeAuthorities + groupAuthorities
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        cognitoGroupsGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>>,
    ): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/admin/**")
                    .authenticated()
                    .requestMatchers("/profile/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }

        if (issuerUri.isNotBlank()) {
            val jwtAuthenticationConverter =
                JwtAuthenticationConverter().also { converter ->
                    converter.setJwtGrantedAuthoritiesConverter(cognitoGroupsGrantedAuthoritiesConverter)
                }

            http.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
        }

        return http.build()
    }
}

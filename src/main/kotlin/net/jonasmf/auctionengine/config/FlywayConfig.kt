package net.jonasmf.auctionengine.config

import org.flywaydb.core.Flyway
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class FlywayConfig {
    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { }

    @Bean
    fun flywayMigrationRunner(flyway: Flyway): ApplicationRunner =
        ApplicationRunner {
            flyway.migrate()
        }
}

package net.jonasmf.auctionengine.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["net.jonasmf.auctionengine.realm.repository", "net.jonasmf.auctionengine.auction.repository", "net.jonasmf.auctionengine.profession.repository", "net.jonasmf.auctionengine.admin.auctionhouse"])
@EntityScan(basePackages = ["net.jonasmf.auctionengine.dbo.rds"])
class JpaConfig

package net.jonasmf.auctionengine.profession.dto

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.integration.blizzard.dto.Href
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.locale.LocaleDTO

data class ProfessionMinimalDTO(
    @JsonProperty("id")
    val id: Int,
    val name: LocaleDTO,
    val key: Href,
)

data class ProfessionIndexDTO(
    @JsonProperty("_links")
    val links: Links,
    val professions: List<ProfessionMinimalDTO>,
)

package net.jonasmf.auctionengine.dto.profession

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.LocaleDTO

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

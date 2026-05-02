package net.jonasmf.auctionengine.dto

data class LocaleTypeValue<T>(
    val id: Int?,
    val type: T?,
    val name: LocaleDTO?,
)

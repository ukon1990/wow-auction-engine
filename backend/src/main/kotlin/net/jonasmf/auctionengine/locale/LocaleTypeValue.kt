package net.jonasmf.auctionengine.locale

data class LocaleTypeValue<T>(
    val id: Int?,
    val type: T?,
    val name: LocaleDTO?,
)

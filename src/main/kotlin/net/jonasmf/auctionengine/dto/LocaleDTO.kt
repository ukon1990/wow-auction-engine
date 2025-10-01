package net.jonasmf.auctionengine.dto

import net.jonasmf.auctionengine.constant.Locale

data class LocaleDTO(
    val en_US: String,
    val es_MX: String,
    val pt_BR: String,
    val pt_PT: String?,
    val de_DE: String,
    val en_GB: String,
    val es_ES: String,
    val fr_FR: String,
    val it_IT: String,
    val ru_RU: String,
    val ko_KR: String,
    val zh_TW: String,
    val zh_CN: String,
) {
    // Convert LocaleDTO to a map based on Locale enum
    fun toMap(): Map<Locale, String?> =
        mapOf(
            Locale.EN_US to en_US,
            Locale.ES_MX to es_MX,
            Locale.PT_BR to pt_BR,
            Locale.PT_PT to pt_PT,
            Locale.DE_DE to de_DE,
            Locale.EN_GB to en_GB,
            Locale.ES_ES to es_ES,
            Locale.FR_FR to fr_FR,
            Locale.IT_IT to it_IT,
            Locale.RU_RU to ru_RU,
            Locale.KO_KR to ko_KR,
            Locale.ZH_TW to zh_TW,
            Locale.ZH_CN to zh_CN,
        )
}

fun localeToProperty(
    locale: Locale,
    localeDTO: LocaleDTO,
): String? =
    when (locale) {
        Locale.EN_US -> localeDTO.en_US
        Locale.ES_MX -> localeDTO.es_MX
        Locale.PT_BR -> localeDTO.pt_BR
        Locale.PT_PT -> localeDTO.pt_PT ?: localeDTO.pt_BR // Provide a default value or handle null
        Locale.DE_DE -> localeDTO.de_DE
        Locale.EN_GB -> localeDTO.en_GB
        Locale.ES_ES -> localeDTO.es_ES
        Locale.FR_FR -> localeDTO.fr_FR
        Locale.IT_IT -> localeDTO.it_IT
        Locale.RU_RU -> localeDTO.ru_RU
        Locale.KO_KR -> localeDTO.ko_KR
        Locale.ZH_TW -> localeDTO.zh_TW
        Locale.ZH_CN -> localeDTO.zh_CN
    }

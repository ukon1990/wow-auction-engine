package net.jonasmf.auctionengine.constant

enum class Locale(
    val value: String,
) {
    EN_US("en_US"),
    PT_BR("pt_BR"),
    PT_PT("pt_PT"),
    ES_MX("es_MX"),
    DE_DE("de_DE"),
    EN_GB("en_GB"),
    ES_ES("es_ES"),
    FR_FR("fr_FR"),
    IT_IT("it_IT"),
    RU_RU("ru_RU"),
    KO_KR("ko_KR"),
    ZH_TW("zh_TW"),
    ZH_CN("zh_CN"),
    ;

    companion object {
        fun getAllValues(): Map<String, Locale> = values().associateBy { it.value }

        fun fromCompactString(value: String): Locale =
            when (value) {
                "enUS" -> EN_US
                "ptBR" -> PT_BR
                "ptPT" -> PT_PT
                "esMX" -> ES_MX
                "deDE" -> DE_DE
                "enGB" -> EN_GB
                "esES" -> ES_ES
                "frFR" -> FR_FR
                "itIT" -> IT_IT
                "ruRU" -> RU_RU
                "koKR" -> KO_KR
                "zhTW" -> ZH_TW
                "zhCN" -> ZH_CN
                else -> throw IllegalArgumentException("Unknown locale: $value")
            }
    }
}

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

        fun fromOrdinalInt(value: Int): Locale =
            when (value) {
                0 -> EN_US
                1 -> PT_BR
                2 -> PT_PT
                3 -> ES_MX
                4 -> DE_DE
                5 -> EN_GB
                6 -> ES_ES
                7 -> FR_FR
                9 -> IT_IT
                10 -> RU_RU
                11 -> KO_KR
                12 -> ZH_TW
                13 -> ZH_CN
                else -> throw IllegalArgumentException("Unknown locale: $value")
            }
    }
}

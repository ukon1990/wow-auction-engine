package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.generated.model.GameLocale

fun GameLocale.toLocaleDTO(): LocaleDTO =
    LocaleDTO(
        en_US = enUS,
        en_GB = enGB,
        de_DE = deDE,
        es_ES = esES,
        es_MX = esMX,
        fr_FR = frFR,
        it_IT = itIT,
        ko_KR = koKR,
        pt_BR = ptBR,
        pt_PT = ptPT,
        ru_RU = ruRU,
        zh_CN = zhCN,
        zh_TW = zhTW,
    )

fun LocaleDTO.toGameLocale(): GameLocale =
    GameLocale(
        enUS = en_US,
        enGB = en_GB,
        deDE = de_DE,
        esES = es_ES,
        esMX = es_MX,
        frFR = fr_FR,
        itIT = it_IT,
        koKR = ko_KR,
        ptBR = pt_BR,
        ptPT = pt_PT,
        ruRU = ru_RU,
        zhCN = zh_CN,
        zhTW = zh_TW,
    )

fun LocaleDTO.hasEnglishName(): Boolean = !en_US.isNullOrBlank() || !en_GB.isNullOrBlank()

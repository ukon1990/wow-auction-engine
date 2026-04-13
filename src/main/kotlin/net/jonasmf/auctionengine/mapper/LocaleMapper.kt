package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.rds.LocaleDBO
import net.jonasmf.auctionengine.dto.LocaleDTO

fun LocaleDTO.toDBO(
    sourceType: String,
    sourceKey: String,
    sourceField: String,
) =
    LocaleDBO(
        sourceType = sourceType,
        sourceKey = sourceKey,
        sourceField = sourceField,
        en_US = en_US,
        en_GB = en_GB,
        es_ES = es_ES,
        es_MX = es_MX,
        de_DE = de_DE,
        pt_PT = pt_PT,
        pt_BR = pt_BR,
        fr_FR = fr_FR,
        it_IT = it_IT,
        ru_RU = ru_RU,
        ko_KR = ko_KR,
        zh_TW = zh_TW,
        zh_CN = zh_CN,
    )

fun LocaleDBO.toDTO() =
    LocaleDTO(
        en_US = en_US,
        en_GB = en_GB,
        es_ES = es_ES,
        es_MX = es_MX,
        de_DE = de_DE,
        pt_PT = pt_PT,
        pt_BR = pt_BR,
        fr_FR = fr_FR,
        it_IT = it_IT,
        ru_RU = ru_RU,
        ko_KR = ko_KR,
        zh_TW = zh_TW,
        zh_CN = zh_CN,
    )

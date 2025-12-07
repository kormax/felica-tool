package com.kormax.felicatool.util

import androidx.annotation.DrawableRes
import com.kormax.felicatool.R

/** Maps known service provider names to their representative drawable resources. */
object ServiceIconMapper {
    private val iconMap =
        mapOf(
            "ICSCA" to R.drawable.service_icsca,
            "PASPY" to R.drawable.service_paspy,
            "KINOCA" to R.drawable.service_kinoca,
            "HARECA" to R.drawable.service_hareca,
            "AOPASS" to R.drawable.service_aopass,
            "AYUCA" to R.drawable.service_ayuca,
            "CJRC" to R.drawable.service_cjrc,
            "EMICA" to R.drawable.service_emica,
            "FELICA_KAZASU_FOLDER" to R.drawable.service_felica_kazasu_folder,
            "FELICA_LITE" to R.drawable.service_felica_lite,
            "OSAIFU_KEITAI" to R.drawable.service_osaifu_keitai,
            "FELICA_NETWORKS" to R.drawable.service_felica_networks,
            "FELICA_POCKET" to R.drawable.service_felica_pocket,
            "HAYAKAKEN" to R.drawable.service_hayakaken,
            "ICOCA" to R.drawable.service_icoca,
            "IRUCA" to R.drawable.service_iruca,
            "KITACA" to R.drawable.service_kitaca,
            "KUMAMON" to R.drawable.service_kumamon,
            "MANACA" to R.drawable.service_manaca,
            "NANACO" to R.drawable.service_nanaco,
            "NDEF" to R.drawable.service_ndef,
            "NIMOCA" to R.drawable.service_nimoca,
            "OCTOPUS" to R.drawable.service_octopus,
            "OKICA" to R.drawable.service_okica,
            "PASMO" to R.drawable.service_pasmo,
            "RAKUTEN_EDY" to R.drawable.service_rakuten_edy,
            "RAPICA" to R.drawable.service_rapica,
            "SAPICA" to R.drawable.service_sapica,
            "SHENZEN_TONG" to R.drawable.service_shenzen_tong,
            "SUGOCA" to R.drawable.service_sugoca,
            "SUICA" to R.drawable.service_suica,
            "SUICA_PLUS" to R.drawable.service_suica_plus,
            "TOICA" to R.drawable.service_toica,
            "WAON" to R.drawable.service_waon,
            "ASACA_DOCARD" to R.drawable.service_asaca_docard,
            "NORUCA" to R.drawable.service_noruca,
            "IBAPPI" to R.drawable.service_ibappi,
            "NICEPASS" to R.drawable.service_nicepass,
            "RYUTO" to R.drawable.service_ryuto,
            "IYOTETSU" to R.drawable.service_iyotetsu,
        )

    @DrawableRes
    fun iconFor(providerName: String): Int? {
        val key = providerName.trim().uppercase()
        return iconMap[key]
    }
}

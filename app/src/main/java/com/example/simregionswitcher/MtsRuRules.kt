package com.example.simregionswitcher

/** MTS Russia baseline from Xiaomi and matching Pixel APN credentials. Network untested. */
object MtsRuRules {
    const val APPLY="mts_ru_v2"
    const val PREVIOUS_APPLY="mts_ru_v1"
    const val CLEAR="mts_ru_clear_v1"
    const val SHA256="ef72eccc076d23eaeec5314e558fae66238383aeeae62c8fa909ca447a88f13b"
    val expectedFlags=linkedMapOf("carrier_volte_available_bool" to true,"carrier_wfc_ims_available_bool" to true,"carrier_vt_available_bool" to true,"show_4g_for_lte_data_icon_bool" to true,"hide_lte_plus_data_icon_bool" to false,"imssms.sms_over_ims_supported_bool" to true)
    val expectedIntegers=mapOf("carrier_ussd_method_int" to 1)
}

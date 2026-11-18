package com.example.simregionswitcher

/** Experimental Megafon Russia profile, bound to 25002. */
object MegafonRuRules {
    const val PREVIOUS_APPLY="megafon_ru_v1"
    const val APPLY="megafon_ru_v2"
    const val CLEAR="megafon_ru_clear_v1"
    const val SHA256="5e314596847ce9a4b7133b110e9a765f36903e9201efea1c60bf8b4f6392d5e8"
    val expectedFlags=linkedMapOf("carrier_volte_available_bool" to true,"carrier_wfc_ims_available_bool" to true,"carrier_supports_ss_over_ut_bool" to true,"show_4g_for_lte_data_icon_bool" to true,"hide_lte_plus_data_icon_bool" to false,"imssms.sms_over_ims_supported_bool" to true)
    val expectedIntegers=linkedMapOf("carrier_ussd_method_int" to 1)
    val expectedArrays=mapOf("iwlan.epdg_address_priority_int_array" to listOf(0,1))
    val expectedStrings=linkedMapOf("xcap_apn_name_string" to "hos","iwlan.epdg_static_address_string" to "epdg.epc.mnc002.mcc250.pub.3gppnetwork.org")
}

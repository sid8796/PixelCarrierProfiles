package com.example.simregionswitcher

/** Experimental T2 Russia profile, bound to 25020. */
object T2RuRules {
    const val APPLY="t2_ru_v1"
    const val CLEAR="t2_ru_clear_v1"
    const val SHA256="8bdfb462bf3096caddfb47b7449d3171ac36f861f39025edab71963bd8d8c12a"
    val expectedFlags=linkedMapOf("carrier_volte_available_bool" to true,"carrier_wfc_ims_available_bool" to true,"carrier_vt_available_bool" to true,"carrier_supports_ss_over_ut_bool" to true,"show_4g_for_lte_data_icon_bool" to true,"hide_lte_plus_data_icon_bool" to false,"imssms.sms_over_ims_supported_bool" to true)
    val expectedIntegers=linkedMapOf("carrier_ussd_method_int" to 1,"imsss.ut_as_server_port_int" to 80,"bsf.bsf_server_port_int" to 80)
    val expectedStrings=linkedMapOf("xcap_apn_name_string" to "hos","imsss.ut_as_server_fqdn_string" to "xcap.ims.mnc020.mcc250.pub.3gppnetwork.org","bsf.bsf_server_fqdn_string" to "bsf.ims.mnc020.mcc250.pub.3gppnetwork.org")
}

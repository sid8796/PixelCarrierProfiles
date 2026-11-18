package com.example.simregionswitcher

/** Experimental Beeline Russia profile, bound to 25099. */
object BeelineRuRules {
    const val APPLY="beeline_ru_v1"
    const val CLEAR="beeline_ru_clear_v1"
    const val SHA256="7b734981e455060a601a55d39136e5257f029712c6cb89d6eed30038b22fefc6"
    val expectedFlags=linkedMapOf("carrier_volte_available_bool" to true,"carrier_wfc_ims_available_bool" to true,"carrier_supports_ss_over_ut_bool" to true,"show_4g_for_lte_data_icon_bool" to true,"hide_lte_plus_data_icon_bool" to false,"imssms.sms_over_ims_supported_bool" to true)
    val expectedIntegers=linkedMapOf("carrier_ussd_method_int" to 1,"imsss.ut_as_server_port_int" to 81)
    val expectedStrings=linkedMapOf("xcap_apn_name_string" to "xcap.beeline.ru","imsss.ut_as_server_fqdn_string" to "xcap.ims.mnc099.mcc250.pub.3gppnetwork.org","bsf.bsf_server_fqdn_string" to "bsf.mnc099.mcc250.pub.3gppnetwork.org","default_vm_number_string" to "600")
}

package com.example.simregionswitcher

/** Prepared Samsung-derived MTS BY profile; device validation is still pending. */
object MtsByRules {
    const val APPLY="mts_by_v1"
    const val CLEAR="mts_by_clear_v1"
    const val SHA256="d1d374e6de298d0092cd23dceddcb362f0f8e755c89db20679e1e8a82d29d544"
    val expectedFlags=linkedMapOf("carrier_volte_available_bool" to true,"carrier_wfc_ims_available_bool" to false,"show_4g_for_lte_data_icon_bool" to true,"hide_lte_plus_data_icon_bool" to false,"carrier_supports_ss_over_ut_bool" to true)
    val expectedIntegers=linkedMapOf("carrier_ussd_method_int" to 1,"imsss.ut_as_server_port_int" to 80,"bsf.bsf_server_port_int" to 8080)
    val expectedStrings=linkedMapOf("xcap_apn_name_string" to "xcap","imsss.ut_as_server_fqdn_string" to "xcap.ims.mnc002.mcc257.pub.3gppnetwork.org","bsf.bsf_server_fqdn_string" to "bsf.mnc002.mcc257.pub.3gppnetwork.org")
}

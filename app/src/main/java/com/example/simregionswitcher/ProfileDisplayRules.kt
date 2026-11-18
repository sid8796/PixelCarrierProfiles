package com.example.simregionswitcher

/** Presentation only. RegionPlan remains the authority for permitting operations. */
object ProfileDisplayRules {
    fun inputReason(mcc: String,mnc: String): String? = when {
        mcc.isEmpty() -> "MCC_EMPTY"
        !mcc.matches(Regex("[0-9]{3}")) -> "MCC_FORMAT"
        mnc.isEmpty() -> "MNC_EMPTY"
        !mnc.matches(Regex("[0-9]{2,3}")) -> "MNC_FORMAT"
        else -> null
    }
    fun apnVerified(code: String?)=code in setOf("APN_VERIFIED","APN_ALREADY_SELECTED")
}

package com.example.simregionswitcher

/** Read-only stages deliberately has no IMS command builder or execution path. */
data class CarrierProfile(val label: String, val mcc: String, val mnc: String)

object ProfileValidator {
    fun isValid(profile: CarrierProfile): Boolean =
        profile.mcc.matches(Regex("[0-9]{3}")) && profile.mnc.matches(Regex("[0-9]{2,3}"))
}


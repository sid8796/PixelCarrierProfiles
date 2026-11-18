package com.example.simregionswitcher

/** Fixed experiment. No caller-supplied subscription, MCC/MNC, or component. */
object A1TrialRules {
    const val SLOT = 0
    const val SUB = 3
    const val MCC = "222"
    const val MNC = "88"
    const val MODIFIER = "io.github.vvb2060.ims.privileged.ImsModifier"
    const val CONFIG = "io.github.vvb2060.ims.privileged.ConfigReader"
    fun matches(slot: Int, sub: Int, mcc: String?, mnc: String?, embedded: Boolean) =
        slot == SLOT && sub == SUB && mcc == "257" && mnc == "01" && !embedded
    fun allowedComponent(name: String) = name == MODIFIER || name == CONFIG
    fun ticketValid(expected: String?, received: String, created: Long, now: Long) =
        expected != null && expected == received && now >= created && now - created <= 300_000
}

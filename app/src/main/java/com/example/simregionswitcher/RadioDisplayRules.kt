package com.example.simregionswitcher

/** Public TelephonyManager RAT values. No claim about IMS transport or active call codec. */
object RadioDisplayRules {
    fun family(type: Int): String? = when (type) {
        1, 2, 4, 7, 11, 16 -> "2G"
        3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
        13, 19 -> "4G"
        20 -> "5G NR"
        else -> null
    }
    fun dbm(value: Int?): Int? = value?.takeIf { it in -150..-20 }
    fun level(value: Int?) = value?.takeIf { it in 0..4 } ?: 0
}

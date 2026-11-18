package com.example.simregionswitcher

enum class ImsAccess { NOT_APPLICABLE, CELLULAR, WLAN, UNKNOWN }
data class TransportSnapshot(val state: Int, val access: ImsAccess, val rawTransport: Int?)

/** WWAN is not LTE-specific; WLAN also includes the platform Cross-SIM mapping. */
object TransportRules {
    fun decode(before: Any?, after: Any?, transport: Any?): ParsedRead {
        if (before !is Int || after !is Int || before !in 0..2 || after !in 0..2)
            return ParsedRead(error = "INVALID_REGISTRATION_STATE")
        if (before != after) return ParsedRead(error = "IMS_STATE_CHANGED")
        val access = when {
            after != 2 -> ImsAccess.NOT_APPLICABLE
            transport is Int && transport == 1 -> ImsAccess.CELLULAR
            transport is Int && transport == 2 -> ImsAccess.WLAN
            else -> ImsAccess.UNKNOWN
        }
        return ParsedRead(transport = TransportSnapshot(after, access, transport as? Int))
    }
}

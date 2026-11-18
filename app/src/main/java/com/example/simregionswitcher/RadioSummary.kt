package com.example.simregionswitcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager

/** Finite read-only getters; adapted from the first version's radio summary. */
object RadioSummary {
    data class Reading(val network: String? = null, val dbm: Int? = null, val level: Int = 0, val carrierId: Int? = null, val carrierName: String? = null)
    fun read(context: Context, subId: Int): Reading {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return Reading()
        val tm = try { context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(subId) }
            catch (_: RuntimeException) { null } ?: return Reading()
        // Read the Android carrier identity independently of network/signal failures.
        // Do not substitute catalog values or the draft MCC/MNC profile.
        val identity = try {
            val id = tm.simCarrierId
            val name = tm.simCarrierIdName?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
            if (id >= 0 && id == tm.simCarrierId) id to name else null
        } catch (_: RuntimeException) { null }
        val radio = try {
            val data = tm.dataNetworkType
            val voice = tm.voiceNetworkType
            val type = when {
                data != TelephonyManager.NETWORK_TYPE_UNKNOWN && data != TelephonyManager.NETWORK_TYPE_IWLAN -> data
                voice != TelephonyManager.NETWORK_TYPE_UNKNOWN && voice != TelephonyManager.NETWORK_TYPE_IWLAN -> voice
                else -> TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
            val cells = tm.signalStrength?.cellSignalStrengths.orEmpty()
            val preferredName = when (RadioDisplayRules.family(type)) {
                "4G" -> "CellSignalStrengthLte"
                "5G NR" -> "CellSignalStrengthNr"
                "3G" -> "CellSignalStrengthWcdma"
                "2G" -> "CellSignalStrengthGsm"
                else -> ""
            }
            val cell = cells.firstOrNull { it.javaClass.simpleName == preferredName && RadioDisplayRules.dbm(it.dbm) != null }
                ?: cells.firstOrNull { RadioDisplayRules.dbm(it.dbm) != null }
            Reading(RadioDisplayRules.family(type), RadioDisplayRules.dbm(cell?.dbm), RadioDisplayRules.level(cell?.level))
        } catch (_: RuntimeException) { Reading() }
        return radio.copy(carrierId = identity?.first, carrierName = identity?.second)
    }
}


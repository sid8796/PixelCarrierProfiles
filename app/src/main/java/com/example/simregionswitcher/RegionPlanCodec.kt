package com.example.simregionswitcher

import android.os.Bundle
import org.json.JSONObject

object RegionPlanCodec {
    fun bundle(plan: RegionPlan) = Bundle().apply {
        putInt("slot", plan.slot); putInt("sub_id", plan.subId); putString("operator", plan.operator)
        putString("source_mcc", plan.sourceMcc); putString("source_mnc", plan.sourceMnc)
        putBoolean("embedded", plan.embedded); putString("mcc", plan.mcc); putString("mnc", plan.mnc); putBoolean("reset", plan.reset); putString("config_mode",plan.configMode); putBoolean("persist_config",plan.persistConfig)
    }
    @Suppress("DEPRECATION") fun decode(raw: Bundle?): RegionPlan? {
        if (raw == null || raw.get("slot") !is Int || raw.get("sub_id") !is Int || raw.get("embedded") !is Boolean) return null
        if (raw.containsKey("reset") && raw.get("reset") !is Boolean) return null
        if(raw.containsKey("persist_config") && raw.get("persist_config") !is Boolean) return null
        if(raw.containsKey("config_mode") && raw.get("config_mode") !is String) return null
        val plan = RegionPlan(raw.getInt("slot"), raw.getInt("sub_id"), raw.getString("operator") ?: return null,
            raw.getString("source_mcc") ?: return null, raw.getString("source_mnc") ?: return null,
            raw.getBoolean("embedded"), raw.getString("mcc") ?: return null, raw.getString("mnc") ?: return null, raw.getBoolean("reset", false),(raw.getString("config_mode") ?: ""),raw.getBoolean("persist_config",false))
        return plan.takeIf { it.valid() }
    }
    fun json(plan: RegionPlan): String = JSONObject().apply {
        put("slot", plan.slot); put("sub_id", plan.subId); put("operator", plan.operator)
        put("source_mcc", plan.sourceMcc); put("source_mnc", plan.sourceMnc); put("embedded", plan.embedded)
        put("mcc", plan.mcc); put("mnc", plan.mnc); put("reset", plan.reset); put("config_mode",plan.configMode); put("persist_config",plan.persistConfig)
    }.toString()
    fun restore(value: String?): RegionPlan? = try {
        val j = JSONObject(value ?: "")
        require(!j.has("reset") || j.get("reset") is Boolean)
        require(!j.has("persist_config") || j.get("persist_config") is Boolean)
        require(!j.has("config_mode") || j.get("config_mode") is String)
        RegionPlan(j.getInt("slot"), j.getInt("sub_id"), j.getString("operator"), j.getString("source_mcc"),
            j.getString("source_mnc"), j.getBoolean("embedded"), j.getString("mcc"), j.getString("mnc"), j.optBoolean("reset", false),j.optString("config_mode", ""),j.optBoolean("persist_config",false)).takeIf { it.valid() }
    } catch (_: Exception) { null }
}

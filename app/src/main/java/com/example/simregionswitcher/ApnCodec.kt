package com.example.simregionswitcher

import android.os.Bundle
import org.json.JSONObject

data class ApnTarget(val plan: RegionPlan, val cardId: Int) {
    fun bundle() = Bundle().apply { putBundle("plan", RegionPlanCodec.bundle(plan)); putInt("card", cardId) }
    fun json() = JSONObject().put("plan", RegionPlanCodec.json(plan)).put("card", cardId).toString()
    companion object {
        @Suppress("DEPRECATION") fun decode(b: Bundle): ApnTarget {
            require(b.get("card") is Int)
            val p = requireNotNull(RegionPlanCodec.decode(b.getBundle("plan")))
            require(p.mcc == p.sourceMcc && p.mnc == p.sourceMnc)
            return ApnTarget(p, b.getInt("card")).also { require(it.cardId >= 0) { "CARD_ID_UNAVAILABLE" } }
        }
        fun restore(s: String): ApnTarget { val j = JSONObject(s); return ApnTarget(requireNotNull(RegionPlanCodec.restore(j.getString("plan"))), j.getInt("card")) }
    }
}
object ApnCodec {
    fun json(r: ApnRow) = JSONObject().apply {
        put("id",r.id); put("name",r.name); put("apn",r.apn); put("numeric",r.numeric)
        put("type",r.type); put("protocol",r.protocol); put("roaming",r.roaming); put("enabled",r.enabled)
    }.toString()
    fun row(s: String): ApnRow { require(s.length < 5000); val j=JSONObject(s); return ApnRow(j.getLong("id"),j.getString("name"),j.getString("apn"),j.getString("numeric"),j.getString("type"),j.getString("protocol"),j.getString("roaming"),j.getBoolean("enabled")).also { require(it.id >= 0) } }
}

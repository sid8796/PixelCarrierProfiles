package com.example.simregionswitcher

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the latest UI trace locally, without numbers, IMS URIs or APN credentials. */
object OperationJournal {
    private var context: Context?=null
    var latest: OperationTrace?=null; private set
    fun initialize(app: Context) {
        if(context!=null) return
        context=app.applicationContext
        latest=runCatching {
            val raw=prefs().getString("latest",null) ?: return@runCatching null
            val j=JSONObject(raw)
            require(j.getInt("schema")==1 && j.getInt("slot") in 0..1)
            val trace=OperationTrace(j.getInt("slot"),j.getInt("sub"),j.getString("mcc"),j.getString("mnc"),j.getBoolean("reset"),j.getBoolean("apn"),j.getLong("wall"),j.getLong("elapsed"),j.optString("config_mode",""))
            val rows=j.getJSONArray("events");require(rows.length()<=24)
            trace.restore((0 until rows.length()).map { i -> val row=rows.getJSONObject(i);OperationTrace.Event(row.getString("code"),row.getLong("wall"),row.getLong("after")) },j.getString("outcome"),if(j.has("duration")) j.getLong("duration") else null,j.optBoolean("waiting"))
            if(trace.active) trace.finish("INTERRUPTED",System.currentTimeMillis(),SystemClock.elapsedRealtime())
            trace
        }.getOrNull()
        save()
    }
    private fun prefs()=context!!.getSharedPreferences("operation_journal",Context.MODE_PRIVATE)
    private fun save() {
        val t=latest ?: return
        val j=JSONObject().put("schema",1).put("slot",t.slot).put("sub",t.subId).put("mcc",t.mcc).put("mnc",t.mnc)
            .put("reset",t.reset).put("apn",t.restoreApn).put("wall",t.startedWall).put("elapsed",t.startedElapsed)
            .put("config_mode",t.configMode).put("outcome",t.outcome).put("waiting",t.awaitingApn)
        t.durationMs?.let { j.put("duration",it) }
        j.put("events",JSONArray().apply { t.events.forEach { put(JSONObject().put("code",it.code).put("wall",it.wall).put("after",it.afterMs)) } })
        prefs().edit().putString("latest",j.toString()).apply()
    }
    fun begin(plan: RegionPlan,restoreApn: Boolean) {
        latest=OperationTrace(plan.slot,plan.subId,plan.mcc,plan.mnc,plan.reset,restoreApn && !plan.reset && !plan.configOnly,System.currentTimeMillis(),SystemClock.elapsedRealtime(),plan.configMode).also {
            it.record("START",System.currentTimeMillis(),SystemClock.elapsedRealtime())
        };save()
    }
    fun observe(regionCode: String,regionBusy: Boolean,autoApnCode: String?,apnCode: String,apnPending: Boolean,apnBusy: Boolean,apnSlot: Int?,apnSub: Int?) {
        val t=latest?.takeIf { it.active } ?: return
        val wall=System.currentTimeMillis();val elapsed=SystemClock.elapsedRealtime()
        var changed=false
        fun event(code: String) { changed=t.record(code,wall,elapsed)||changed }
        fun end(code: String) { changed=t.finish(code,wall,elapsed)||changed }
        when(regionCode) {
            "APN_BACKUP_SAVING" -> event("APN_BACKUP")
            "APPLYING" -> event(when(t.configMode) { LocalPbRules.APPLY,MtsByRules.APPLY,MtsRuRules.APPLY,BeelineRuRules.APPLY,MegafonRuRules.APPLY,T2RuRules.APPLY -> "LOCAL_PB_APPLY";LocalPbRules.CLEAR,MtsByRules.CLEAR,MtsRuRules.CLEAR,BeelineRuRules.CLEAR,MegafonRuRules.CLEAR,T2RuRules.CLEAR -> "LOCAL_PB_CLEAR";else -> if(t.reset) "RESET" else "APPLY" })
            "PENDING_UNKNOWN","OUTCOME_UNCERTAIN","INSPECT_FAILED","REQUEST_NOT_TRACKED","LOCKED","INVALID_REPLY" -> event("WAIT")
            "APN_RESTORING" -> { event("PROFILE_OK");event("APN_RESTORE") }
        }
        // beginPrivateRead notifies synchronously before RegionSession publishes APPLYING.
        // Do not assign the previous request completion to a newly created trace.
        if(!regionBusy && !t.executionObserved) return
        if(!regionBusy) {
            when {
                regionCode=="MODIFIER_REPORTED_SUCCESS" -> {
                    event("PROFILE_OK")
                    if(t.restoreApn && autoApnCode!=null) {
                        val code=if(t.awaitingApn && apnSlot==t.slot && apnSub==t.subId) apnCode else autoApnCode
                        if(apnPending || apnBusy || code in setOf("APN_PENDING","APN_NOT_TRACKED","WORKING")) {
                            event("APN_WAIT");t.awaitingApn=true;changed=true
                        } else {
                            event(code)
                            end(if(code in setOf("APN_VERIFIED","APN_ALREADY_SELECTED")) "SUCCESS" else "APN_ATTENTION")
                        }
                    } else if(!t.restoreApn) end("SUCCESS")
                    else { event("APN_UNCONFIRMED");end("APN_ATTENTION") }
                }
                regionCode in RegionGuard.completedApply || regionCode=="APN_BACKUP_FAILED" -> { event(regionCode);end("FAILED") }
            }
        }
        if(changed) save()
    }
}

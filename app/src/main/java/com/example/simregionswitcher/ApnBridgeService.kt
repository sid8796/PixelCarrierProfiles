package com.example.simregionswitcher

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import android.telephony.SubscriptionInfo
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Separate, narrow writer. Only preferapn/subId apn_id may be updated, never APN records. */
class ApnBridgeService(private val context: Context) : IApnBridge.Stub() {
    private val appUid = context.applicationInfo.uid
    private var target: ApnTarget? = null
    private var before: ApnRow? = null
    private var candidate: ApnRow? = null
    private var ticket = ""
    private var created = 0L
    private var attempted = false
    private var process: Process? = null
    private var final: Bundle? = null
    private fun packet(code: String, row: ApnRow? = null) = Bundle().apply {
        putString("code", code); row?.let { putString("current", ApnCodec.json(it)) }
    }
    private fun caller() { if (Binder.getCallingUid() != appUid) throw SecurityException("Unexpected caller") }
    private fun mapped(t: ApnTarget) {
        val p=t.plan
        val service = PlatformTransportReader.service("isub", "com.android.internal.telephony.ISub\$Stub")
        val type = Class.forName("com.android.internal.telephony.ISub")
        val observed=type.getMethod("getSubId", Int::class.javaPrimitiveType).invoke(service,p.slot)
        check(BinderMapping.assess(p.subId,observed).error == null) { "MAPPING_CHANGED" }
        val method=type.methods.singleOrNull { it.name=="getActiveSubscriptionInfo" &&
            (it.parameterTypes.toList()==listOf(Int::class.javaPrimitiveType,String::class.java,String::class.java) ||
             it.parameterTypes.toList()==listOf(Int::class.javaPrimitiveType,String::class.java,String::class.java,Boolean::class.javaPrimitiveType)) } ?: error("SUBSCRIPTION_API_UNAVAILABLE")
        val pkg=if(android.os.Process.myUid()==2000) "com.android.shell" else context.packageName
        val args=if(method.parameterCount==3) arrayOf(p.subId,pkg,null) else arrayOf(p.subId,pkg,null,false)
        val info=method.invoke(service,*args) as? SubscriptionInfo ?: error("MAPPING_CHANGED")
        check(p.matches(info.simSlotIndex,info.subscriptionId,info.mccString,info.mncString,info.isEmbedded) && info.cardId==t.cardId) { "MAPPING_CHANGED" }
    }
    private fun start(args: List<String>) = ProcessBuilder(listOf("/system/bin/content")+args+listOf("--user",(appUid/100000).toString())).redirectErrorStream(true).start()
    private fun query(uri: String): List<ApnRow> {
        val p=start(listOf("query","--uri",uri,"--projection",ApnRules.columns.joinToString(":")))
        val output=StringBuilder(); var readError=false
        val reader=Thread {
            try { p.inputStream.bufferedReader().use { input ->
                val buffer=CharArray(2048)
                while(true) { val n=input.read(buffer); if(n<0) break
                    if(output.length+n>100000) { readError=true; p.destroy(); break }; output.append(buffer,0,n)
                }
            } } catch (_: Exception) { readError=true }
        }.apply { isDaemon=true; start() }
        if(!p.waitFor(12,TimeUnit.SECONDS)) { p.destroy(); error("APN_READ_TIMEOUT") }
        reader.join(2000)
        check(!reader.isAlive && !readError && p.exitValue()==0) { "APN_READ_FAILED" }
        return ApnRules.parse(output.toString())
    }
    private fun preferred(t: ApnTarget)=query("content://telephony/carriers/preferapn/subId/${t.plan.subId}").singleOrNull() ?: error("APN_NO_PREFERRED")
    private fun eligible(t: ApnTarget)=query("content://telephony/carriers/sim_apn_list/filtered/subId/${t.plan.subId}")
    private fun read(t: ApnTarget): Bundle { mapped(t); val r=preferred(t); mapped(t); return packet("READ",r).apply { putBundle("target",t.bundle()) } }
    @Synchronized override fun inspect(raw: Bundle): Bundle {
        caller(); val id=Binder.clearCallingIdentity()
        return try { read(ApnTarget.decode(raw)) } catch (_: Exception) { packet("APN_READ_FAILED") }
        finally { Binder.restoreCallingIdentity(id) }
    }
    @Synchronized override fun prepare(raw: Bundle, savedRow: String): Bundle = prepareInternal(raw, savedRow, null)
    @Synchronized override fun prepareChoice(raw: Bundle, savedRow: String, rowId: Long): Bundle = prepareInternal(raw, savedRow, rowId)
    private fun prepareInternal(raw: Bundle, savedRow: String, chosenId: Long?): Bundle {
        caller(); val id=Binder.clearCallingIdentity()
        return try {
            check(!attempted || final != null) { "APN_PENDING" }
            val t=ApnTarget.decode(raw); mapped(t)
            val saved=ApnCodec.row(savedRow); check(saved.internet()) { "APN_NOT_INTERNET" }
            val current=preferred(t)
            val matches=ApnRules.candidates(saved,eligible(t))
            val next = if (chosenId == null) ApnRules.candidate(saved,matches)
                else matches.singleOrNull { it.id==chosenId }
            if(next == null) {
                if(chosenId==null && matches.size>1) return packet("APN_CHOICE_REQUIRED",current).apply {
                    putBundle("target",t.bundle()); putStringArrayList("choices",ArrayList(matches.map { ApnCodec.json(it) }))
                }
                return packet("APN_NO_UNIQUE_MATCH",current).apply { putBundle("target",t.bundle()) }
            }
            mapped(t)
            if(current==next) return packet("APN_ALREADY_SELECTED",current).apply { putBundle("target",t.bundle()) }
            target=t; before=current; candidate=next; ticket=UUID.randomUUID().toString()
            created=SystemClock.elapsedRealtime(); attempted=false; final=null; process=null
            packet("PREPARED",current).apply { putString("candidate",ApnCodec.json(next)); putString("ticket",ticket); putBundle("target",t.bundle()) }
        } catch (_: Exception) { packet("APN_PRECHECK_FAILED") }
        finally { Binder.restoreCallingIdentity(id) }
    }
    @Synchronized override fun apply(token: String): Bundle {
        caller(); val id=Binder.clearCallingIdentity()
        return try {
            if(token!=ticket || token.isBlank() || attempted || SystemClock.elapsedRealtime()-created !in 0..120000) return packet("APN_TICKET_EXPIRED")
            val t=requireNotNull(target); val next=requireNotNull(candidate)
            mapped(t)
            check(preferred(t)==before && eligible(t).any { it==next && it.internet() }) { "APN_CHANGED" }
            mapped(t)
            attempted=true // never replay this ticket, including if start/IPC fails
            process=start(listOf("update","--uri","content://telephony/carriers/preferapn/subId/${t.plan.subId}","--bind","apn_id:l:${next.id}"))
            if(!process!!.waitFor(15,TimeUnit.SECONDS)) packet("APN_PENDING") else finish()
        } catch (_: Exception) {
            if(attempted) packet("APN_PENDING") else packet("APN_PRECHECK_FAILED")
        } finally { Binder.restoreCallingIdentity(id) }
    }
    private fun finish(): Bundle {
        final?.let { return it }
        val p=process ?: return packet("APN_PENDING")
        if(p.isAlive) return packet("APN_PENDING")
        val t=requireNotNull(target); mapped(t)
        val current=preferred(t); mapped(t)
        // CLI exit code/output alone does not establish success.
        return packet(if(current==candidate) "APN_VERIFIED" else "APN_NOT_SELECTED",current).also { final=it }
    }
    @Synchronized override fun check(token: String): Bundle {
        caller(); val id=Binder.clearCallingIdentity()
        return try { if(token!=ticket || token.isBlank() || !attempted) packet("APN_NOT_TRACKED") else finish() }
        catch (_: Exception) { packet("APN_PENDING") }
        finally { Binder.restoreCallingIdentity(id) }
    }
    override fun destroy() { caller(); /* no automatic process termination or retry */ }
}

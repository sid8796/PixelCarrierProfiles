package com.example.simregionswitcher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** APN snapshot and manual preferred-selection flow, independent of IMS reader/writer. */
object ApnSession {
    data class Saved(val target: ApnTarget, val row: ApnRow, val time: Long, val automatic: Boolean = false)
    data class Preview(val target: ApnTarget, val current: ApnRow, val candidate: ApnRow, val ticket: String)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var app: Context?=null
    private var bridge: IApnBridge?=null
    private var connection: ServiceConnection?=null
    private var bridgeLegacy=false
    private val snapshotLock=Any()
    private var foreground=false
    private val autoTried=mutableSetOf<String>()
    private fun savedKey(t: ApnTarget) = ApnAutoSaveRules.storageKey(t.plan.slot,t.plan.subId,t.cardId,t.plan.sourceMcc,t.plan.sourceMnc)
    private fun restoreKey(t: ApnTarget)=savedKey(t)+"_restore_enabled"
    fun restoreEnabled(t: ApnTarget): Boolean = prefs().getBoolean(restoreKey(t),true)
    fun setRestoreEnabled(t: ApnTarget, enabled: Boolean) {
        if(busy || pending || ReaderSession.state.running || RegionSession.busy || RegionSession.blocksReads) return
        val before=restoreEnabled(t)
        val ok=prefs().edit().putBoolean(restoreKey(t),enabled).commit()
        if(!ok) prefs().edit().putBoolean(restoreKey(t),before).commit()
        if(!same(target,t)) current=null
        target=t
        code=if(!ok) "STORE_FAILED" else if(enabled) "RESTORE_ENABLED" else "RESTORE_DISABLED"
        ReaderSession.notifyPrivateChange()
    }
    fun onForeground() { if(!foreground) autoTried.clear(); foreground=true }
    fun onBackground() { foreground=false }
    fun considerAuto(context: Context, targets: List<ApnTarget>) {
        initialize(context)
        val ready=ReaderSession.accessError()==null
        val occupied=busy || pending || preview!=null || choices.isNotEmpty() || RegionSession.busy || RegionSession.blocksReads || ReaderSession.state.running
        val next=targets.firstOrNull {
            ApnAutoSaveRules.shouldStart(foreground,ready,occupied,saved(it)!=null,savedKey(it) in autoTried)
        } ?: return
        autoTried.add(savedKey(next))
        run(context,"auto",next)
    }
    var choices: List<ApnRow> = emptyList(); private set
    var busy=false; private set
    var pending=false; private set
    var code="NOT_CHECKED"; private set
    var target: ApnTarget?=null; private set
    var current: ApnRow?=null; private set
    var preview: Preview?=null; private set
    private var pendingTicket=""
    private fun prefs()=requireNotNull(app).getSharedPreferences("experimental_apn",Context.MODE_PRIVATE)
    private fun boot()=runCatching { Settings.Global.getInt(app!!.contentResolver,Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
    fun initialize(context: Context) {
        if(app!=null) return
        app=context.applicationContext
        pending=prefs().getBoolean("pending",false)
        if(pending) {
            target=runCatching { ApnTarget.restore(prefs().getString("pending_target","")!!) }.getOrNull()
            pendingTicket=prefs().getString("pending_ticket","") ?: ""
            val priorBoot=prefs().getInt("pending_boot",-1); val nowBoot=boot()
            if(ApnRules.endedAfterReboot(priorBoot,nowBoot) && prefs().edit().putBoolean("pending",false).commit()) {
                pending=false; code="APN_ENDED_AFTER_REBOOT"
            } else { code="APN_PENDING"; if(!RegionSession.blocksReads) ReaderSession.beginPrivateRead() }
        }
    }
    fun same(a: ApnTarget?, b: ApnTarget?) = a!=null && b!=null && a.cardId==b.cardId &&
        a.plan.matches(b.plan.slot,b.plan.subId,b.plan.sourceMcc,b.plan.sourceMnc,b.plan.embedded)
    fun saved(t: ApnTarget?): Saved? = runCatching {
        if(t==null) return null
        val j=JSONObject(prefs().getString(savedKey(t),null) ?: prefs().getString("saved_${t.plan.subId}","") ?: "")
        if(j.optInt("schema",0)!=2) return null
        Saved(ApnTarget.restore(j.getString("target")),ApnCodec.row(j.getString("row")),j.getLong("time"),j.optString("origin", "manual")=="automatic").takeIf { same(it.target,t) }
    }.getOrNull()
    fun canForget(t: ApnTarget?): Boolean = t!=null && saved(t)!=null && !busy && !pending &&
        !RegionSession.busy && !RegionSession.blocksReads && !ReaderSession.state.running
    /** Local metadata only. No content-provider, Shizuku or operator command is issued here. */
    fun forget(t: ApnTarget) {
        if(!canForget(t)) return
        val ok=synchronized(snapshotLock) {
            val edit=prefs().edit().remove(savedKey(t))
            val legacyKey="saved_${t.plan.subId}"
            val legacyMatches=runCatching {
                val legacy=JSONObject(prefs().getString(legacyKey,null) ?: return@runCatching false)
                legacy.optInt("schema",0)==2 && same(ApnTarget.restore(legacy.getString("target")),t)
            }.getOrDefault(false)
            if(legacyMatches) edit.remove(legacyKey)
            edit.commit()
        }
        if(!same(target,t)) current=null
        target=t; preview=null; choices=emptyList()
        code=if(ok) "SNAPSHOT_FORGOTTEN" else "STORE_FAILED"
        if(ok) autoTried.remove(savedKey(t)) else autoTried.add(savedKey(t))
        ReaderSession.notifyPrivateChange()
    }
    private fun persistSaved(t: ApnTarget, row: ApnRow, automatic: Boolean): Boolean {
        val j=JSONObject().put("schema",2).put("origin",if(automatic) "automatic" else "manual")
            .put("target",t.json()).put("row",ApnCodec.json(row)).put("time",System.currentTimeMillis())
        return prefs().edit().putString(savedKey(t),j.toString()).commit()
    }
    private data class Capture(val code: String, val row: ApnRow? = null) {
        val ready get() = code in setOf("AUTO_SAVED","AUTO_PRESERVED")
    }
    private fun captureMissing(t: ApnTarget): Capture = synchronized(snapshotLock) {
        var observed: ApnRow?=null
        if(prefs().contains(savedKey(t)) && saved(t)==null) return@synchronized Capture("APN_SAVED_DATA_INVALID")
        try {
            val result=ApnAutoSaveRules.ensure(existing={saved(t)}, read={
                check(mapped(t)) { "MAPPING_CHANGED" }
                val reply=service()?.inspect(t.bundle()) ?: error("APN_BRIDGE_UNAVAILABLE")
                check(reply.getString("code")=="READ" && same(ApnTarget.decode(requireNotNull(reply.getBundle("target"))),t)) { "APN_READ_FAILED" }
                val row=ApnCodec.row(requireNotNull(reply.getString("current")))
                check(row.internet()) { "APN_NOT_ORIGINAL" }; check(mapped(t)) { "MAPPING_CHANGED" }
                observed=row
                Saved(t,row,0,true)
            }, persist={ persistSaved(t,it.row,true) })
            Capture(when(result) {
                ApnAutoSaveRules.Result.PRESERVED -> "AUTO_PRESERVED"
                ApnAutoSaveRules.Result.SAVED -> "AUTO_SAVED"
                ApnAutoSaveRules.Result.FAILED -> "STORE_FAILED"
            },observed)
        } catch(error: Exception) {
            Capture(error.message?.takeIf { it in setOf("MAPPING_CHANGED","APN_BRIDGE_UNAVAILABLE","APN_READ_FAILED","APN_NOT_ORIGINAL") } ?: "APN_READ_FAILED")
        }
    }
    /** Called only on RegionSession's worker while it already holds the global operation lock. */
    fun ensureBeforeProfileChange(context: Context, plan: RegionPlan, onSaving: () -> Unit = {}): Boolean {
        if(plan.reset) return true
        initialize(context)
        val c=app!!
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) return false
        val info=c.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            .singleOrNull { plan.matches(it.simSlotIndex,it.subscriptionId,it.mccString,it.mncString,it.isEmbedded) } ?: return false
        if(info.cardId<0) return false
        val t=ApnTarget(plan.copy(mcc=plan.sourceMcc,mnc=plan.sourceMnc),info.cardId)
        if(saved(t)!=null) return true
        onSaving()
        main.post { autoTried.add(savedKey(t)); target=t; busy=true; code="AUTO_SAVING"; ReaderSession.notifyPrivateChange() }
        val capture=captureMissing(t)
        main.post { target=t; busy=false; code=capture.code; current=capture.row; ReaderSession.notifyPrivateChange() }
        return capture.ready
    }
    fun snapshotForPlan(context: Context, plan: RegionPlan): Saved? {
        initialize(context)
        if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) return null
        val info=context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            .singleOrNull { plan.matches(it.simSlotIndex,it.subscriptionId,it.mccString,it.mncString,it.isEmbedded) } ?: return null
        if(info.cardId<0) return null
        return saved(ApnTarget(plan.copy(mcc=plan.sourceMcc,mnc=plan.sourceMnc),info.cardId))
    }
    /** Continuation of one confirmed MCC/MNC request. Borrow RegionSession's operation guard. */
    fun restoreAfterProfileChange(frozen: Saved): String {
        val t=frozen.target
        main.post { target=t; busy=true; code="AUTO_RESTORING"; preview=null; choices=emptyList(); ReaderSession.notifyPrivateChange() }
        var sent=false; var token=""; var row: ApnRow?=null
        var foundChoices: List<ApnRow> = emptyList()
        var result=try {
            check(!pending && mapped(t)) { "MAPPING_CHANGED" }
            check(ReaderSession.accessError()==null) { "SHIZUKU_UNAVAILABLE" }
            // Give the just-submitted carrier configuration a bounded settling interval.
            Thread.sleep(750)
            val helper=service() ?: error("APN_BRIDGE_UNAVAILABLE")
            val prepared=helper.prepare(t.bundle(),ApnCodec.json(frozen.row))
            val code=prepared.getString("code") ?: "APN_PRECHECK_FAILED"
            if(code in setOf("PREPARED","APN_ALREADY_SELECTED","APN_CHOICE_REQUIRED","APN_NO_UNIQUE_MATCH")) {
                check(same(ApnTarget.decode(requireNotNull(prepared.getBundle("target"))),t))
                row=prepared.getString("current")?.let { ApnCodec.row(it) }
            }
            when(code) {
                "APN_ALREADY_SELECTED" -> {
                    check(row?.sameProfile(frozen.row)==true)
                    "APN_ALREADY_SELECTED"
                }
                "APN_CHOICE_REQUIRED" -> {
                    foundChoices=prepared.getStringArrayList("choices").orEmpty().map { ApnCodec.row(it) }
                    check(foundChoices.size in 2..500 && foundChoices.all { it.internet() && it.sameProfile(frozen.row) })
                    "APN_CHOICE_REQUIRED"
                }
                "PREPARED" -> {
                    val before=requireNotNull(row)
                    val candidate=ApnCodec.row(requireNotNull(prepared.getString("candidate")))
                    check(candidate.internet() && candidate.sameProfile(frozen.row) && mapped(t))
                    token=requireNotNull(prepared.getString("ticket")); check(token.matches(Regex("[0-9a-f-]{36}")))
                    check(prefs().edit().putBoolean("pending",true).putString("pending_target",t.json())
                        .putString("pending_ticket",token).putInt("pending_boot",boot()).putInt("helper_version",2)
                        .putString("before",ApnCodec.json(before)).putString("candidate",ApnCodec.json(candidate)).commit()) { "STORE_FAILED" }
                    sent=true
                    val reply=helper.apply(token)
                    row=reply.getString("current")?.let { ApnCodec.row(it) }
                    val outcome=reply.getString("code") ?: "APN_PENDING"
                    if(outcome=="APN_VERIFIED") check(row==candidate)
                    outcome
                }
                else -> code
            }
        } catch(error: Exception) {
            if(sent) "APN_PENDING" else error.message?.takeIf {
                it in setOf("MAPPING_CHANGED","SHIZUKU_UNAVAILABLE","APN_BRIDGE_UNAVAILABLE","STORE_FAILED")
            } ?: "APN_PRECHECK_FAILED"
        }
        var hold=ApnRules.uncertain(sent,false,result)
        if(sent && !hold && !prefs().edit().putBoolean("pending",false).commit()) { hold=true; result="APN_PENDING" }
        val finalCode=result; val finalHold=hold; val finalRow=row; val finalChoices=foundChoices; val finalToken=token
        main.post {
            target=t; busy=false; pending=finalHold; code=finalCode; current=finalRow
            pendingTicket=finalToken; choices=if(finalHold) emptyList() else finalChoices
            ReaderSession.notifyPrivateChange() // RegionSession owns release, or retains it for pending APN.
        }
        return result
    }
    private fun mapped(t: ApnTarget): Boolean {
        val c=app!!
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) return false
        return c.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty().count {
            t.plan.matches(it.simSlotIndex,it.subscriptionId,it.mccString,it.mncString,it.isEmbedded) && it.cardId==t.cardId
        }==1
    }
    private fun service(): IApnBridge? {
        val legacy=pending && prefs().getInt("helper_version",1)==1
        bridge?.takeIf { bridgeLegacy==legacy }?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val signal=CountDownLatch(1)
        main.post {
            val conn=object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { bridge=IApnBridge.Stub.asInterface(binder); bridgeLegacy=legacy; signal.countDown() }
                override fun onServiceDisconnected(name: ComponentName?) { bridge=null; signal.countDown() }
            }
            connection=conn
            try { Shizuku.bindUserService(Shizuku.UserServiceArgs(ComponentName(app!!,ApnBridgeService::class.java))
                .tag(if(legacy) "sim-region-experimental-apn" else "sim-region-working-apn").version(if(legacy) 1 else 2).daemon(false).debuggable(false).processNameSuffix(if(legacy) "apn" else "workingapn"),conn) }
            catch (_: RuntimeException) { signal.countDown() }
        }
        if(!signal.await(10,TimeUnit.SECONDS)) return null
        return bridge?.takeIf { bridgeLegacy==legacy && it.asBinder().pingBinder() }
    }
    fun read(context: Context,t: ApnTarget) = run(context,"read",t)
    fun save(context: Context,t: ApnTarget) = run(context,"save",t)
    fun prepare(context: Context,t: ApnTarget) = run(context,"prepare",t)
    fun choose(context: Context,t: ApnTarget,row: ApnRow) { if(same(target,t) && row in choices) run(context,"prepare",t,row.id) }
    fun apply(context: Context,p: Preview) { if(preview==p) run(context,"apply",p.target) }
    fun check(context: Context) { target?.let { run(context,"check",it) } }
    private fun run(context: Context, action: String, t: ApnTarget, chosenId: Long? = null) {
        initialize(context)
        if(busy || RegionSession.blocksReads || (pending && action!="check")) return
        if(!pending && !ReaderSession.beginPrivateRead()) return
        val prepared=preview
        target=t; busy=true; code=if(action=="auto") "AUTO_SAVING" else "WORKING"
        if(action!="apply") preview=null
        choices=emptyList()
        ReaderSession.notifyPrivateChange()
        worker.execute {
            var startedWrite=false
            var reply: Bundle?=null
            var newPreview: Preview?=null
            var newChoices: List<ApnRow> = emptyList()
            var row: ApnRow?=null
            val result=try {
                check(ReaderSession.accessError()==null) { "SHIZUKU_UNAVAILABLE" }
                check(mapped(t)) { "MAPPING_CHANGED" }
                val helper by lazy { service() ?: error("APN_BRIDGE_UNAVAILABLE") }
                when(action) {
                    "auto" -> { val captured=captureMissing(t); row=captured.row; captured.code }
                    "read", "save" -> {
                        reply=helper.inspect(t.bundle())
                        check(reply?.getString("code")=="READ" && same(ApnTarget.decode(requireNotNull(reply!!.getBundle("target"))),t)) { "APN_READ_FAILED" }
                        row=ApnCodec.row(reply!!.getString("current")!!)
                        check(mapped(t)) { "MAPPING_CHANGED" }
                        if(action=="save") {
                            check(row!!.internet()) { "APN_NOT_ORIGINAL" }
                            check(synchronized(snapshotLock) { persistSaved(t,row!!,false) }) { "STORE_FAILED" }
                            "SAVED"
                        } else "READ"
                    }
                    "prepare" -> {
                        val saved=saved(t) ?: error("APN_NO_SNAPSHOT")
                        reply=if(chosenId==null) helper.prepare(t.bundle(),ApnCodec.json(saved.row))
                            else helper.prepareChoice(t.bundle(),ApnCodec.json(saved.row),chosenId)
                        if(reply!!.getString("code")=="PREPARED") {
                            check(same(ApnTarget.decode(requireNotNull(reply!!.getBundle("target"))),t)) { "MAPPING_CHANGED" }
                            val candidate=ApnCodec.row(reply!!.getString("candidate")!!)
                            row=ApnCodec.row(reply!!.getString("current")!!)
                            val ticket=requireNotNull(reply!!.getString("ticket")); check(ticket.matches(Regex("[0-9a-f-]{36}")))
                            check(candidate.internet() && candidate.sameProfile(saved.row))
                            newPreview=Preview(t,row!!,candidate,ticket)
                            "PREPARED"
                        } else {
                            val c=reply!!.getString("code") ?: "APN_PRECHECK_FAILED"
                            if(c in setOf("APN_CHOICE_REQUIRED","APN_ALREADY_SELECTED","APN_NO_UNIQUE_MATCH")) {
                                check(same(ApnTarget.decode(requireNotNull(reply!!.getBundle("target"))),t))
                                row=reply!!.getString("current")?.let { ApnCodec.row(it) }
                            }
                            if(c=="APN_CHOICE_REQUIRED") {
                                newChoices=reply!!.getStringArrayList("choices").orEmpty().map { ApnCodec.row(it) }
                                check(newChoices.size in 2..500 && newChoices.all { it.internet() && it.sameProfile(saved.row) })
                                check(newChoices.map { it.id }.distinct().size==newChoices.size)
                            }
                            c
                        }
                    }
                    "apply" -> {
                        val p=requireNotNull(prepared); check(same(p.target,t) && mapped(t))
                        // Intent and original selection persisted before the one permitted write RPC.
                        check(prefs().edit().putBoolean("pending",true).putString("pending_target",t.json())
                            .putString("pending_ticket",p.ticket).putInt("pending_boot",boot()).putInt("helper_version",2)
                            .putString("before",ApnCodec.json(p.current)).putString("candidate",ApnCodec.json(p.candidate)).commit()) { "STORE_FAILED" }
                        startedWrite=true; pendingTicket=p.ticket
                        reply=helper.apply(p.ticket)
                        reply?.getString("current")?.let { row=ApnCodec.row(it) }
                        reply?.getString("code") ?: "APN_PENDING"
                    }
                    "check" -> {
                        reply=helper.check(pendingTicket)
                        reply?.getString("current")?.let { row=ApnCodec.row(it) }
                        reply?.getString("code") ?: "APN_PENDING"
                    }
                    else -> "APN_PRECHECK_FAILED"
                }
            } catch(error: Exception) {
                if(startedWrite || pending) "APN_PENDING" else when(error.message) {
                    "STORE_FAILED", "APN_NO_SNAPSHOT", "APN_NOT_ORIGINAL", "MAPPING_CHANGED", "APN_BRIDGE_UNAVAILABLE", "SHIZUKU_UNAVAILABLE" -> error.message!!
                    else -> "APN_READ_FAILED"
                }
            }
            val uncertain=ApnRules.uncertain(startedWrite,pending,result)
            var finalCode=result
            var hold=uncertain
            if((startedWrite || pending) && !uncertain && !prefs().edit().putBoolean("pending",false).commit()) { hold=true; finalCode="APN_PENDING" }
            main.post {
                busy=false; pending=hold; code=finalCode; current=row; preview=if(hold) null else newPreview; choices=if(hold) emptyList() else newChoices
                if(hold) ReaderSession.notifyPrivateChange() else ReaderSession.finishPrivateRead()
            }
        }
    }
}

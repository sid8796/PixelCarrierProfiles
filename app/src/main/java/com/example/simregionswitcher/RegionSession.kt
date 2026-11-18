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
import android.util.AtomicFile
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Inspect never starts instrumentation. No automatic apply, reset, cancellation or retry. */
object RegionSession {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var app: android.app.Application? = null
    private var bridge: ICarrierTrial? = null
    private var bridgeChannel: String? = null
    private var connection: ServiceConnection? = null
    var activePlan: RegionPlan? = null; private set
    private var ticket: String? = null
    data class History(val plan: RegionPlan, val code: String, val time: Long)
    fun history(slot: Int): History? {
        val pref = preferences()
        val plan = RegionPlanCodec.restore(pref.getString("slot_${slot}_plan", null)) ?: return null
        return History(plan, pref.getString("slot_${slot}_outcome", "") ?: "", pref.getLong("slot_${slot}_time", 0))
    }
    fun internetVerified(slot:Int):Boolean=preferences().getBoolean("slot_${slot}_internet_verified",false)
    fun canPrepare() = !busy && !ReaderSession.state.running && RegionGuard.canPrepare(locked, blocksReads, phase, code)
    fun canApplyDirect(plan: RegionPlan?) = BundledProfiles.canApply(plan) && canPrepare() && !ApnSession.pending
    fun canApplyFor(displayed: RegionPlan?) = !ReaderSession.state.running && RegionGuard.canApply(activePlan, displayed, ticket != null && code == "PREPARED", locked, busy)
    private var requestId = ""
    private var phase = ""
    private val apnFollowUp=ProfileApnFollowUp<ApnSession.Saved>()
    var autoApnCode: String?=null; private set
    private var visible = false
    private var polling = false
    var busy = false; private set
    var code = "NOT_STARTED"; private set
    var detail = ""; private set
    var snapshotPath = ""; private set
    var locked = false; private set
    var blocksReads = false; private set
    private fun preferences() = app!!.getSharedPreferences("a1_trial", Context.MODE_PRIVATE)
    private fun boot() = Settings.Global.getInt(app!!.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun needsHold(value: String) = value in setOf("PREPARING_INTERRUPTED", "APPLY_INTENT_OUTCOME_UNKNOWN", "PENDING_UNKNOWN", "OUTCOME_UNCERTAIN", "OUTCOME_UNCERTAIN_OR_PRECHECK_FAILED", "LOCKED", "REQUEST_NOT_TRACKED", "INSPECT_FAILED", "INVALID_REPLY", "LEGACY_PREPARATION_NEEDS_REBOOT")
    private fun releaseOrHold() {
        blocksReads = locked && needsHold(code)
        if (blocksReads || ApnSession.pending) ReaderSession.notifyPrivateChange() else ReaderSession.finishPrivateRead()
    }
    fun messageResource(): Int {
        if(activePlan?.configOnly==true) {
            if(code=="CARRIER_REFUSAL_ACKNOWLEDGED") return R.string.persist_refusal_done
            if(code=="PRECHECK_FAILED" && detail.contains("APN_ACCESS_DENIED")) return R.string.pb_apn_denied
            if(code=="PRECHECK_FAILED" && detail.contains("EXISTING_APN_CONFLICT")) return R.string.pb_apn_conflict
            if(code=="PRECHECK_FAILED" && detail.contains("apn-shizuku")) return R.string.pb_apn_check_failed
            if(code=="PRECHECK_FAILED" && detail.contains("original-network-required")) return R.string.pb_restore_first
            if(code=="MODIFIER_REPORTED_SUCCESS") return if(BundledProfiles.isClear(activePlan?.configMode)) R.string.pb_clear_done else if(detail.contains(":internet=")) R.string.pb_apply_done else R.string.pb_apply_done_without_selection
            if(code=="APPLYING") return R.string.pb_working
        }
        if (preferences().getString("backend", "").orEmpty().startsWith("native-v")) {
            if (code == "OUTCOME_UNCERTAIN") return R.string.native_uncertain
            if (code in setOf("PENDING_UNKNOWN", "INSPECT_FAILED", "REQUEST_NOT_TRACKED", "OUTCOME_UNCERTAIN_OR_PRECHECK_FAILED")) return R.string.native_pending
        }
        return when (code) {
        "V1_ATTEMPT_ARCHIVED" -> R.string.native_v1_archived
        "APN_RESTORING" -> R.string.apn_auto_restoring
        "APN_BACKUP_SAVING" -> R.string.apn_auto_working
        "APN_BACKUP_FAILED" -> R.string.apn_backup_profile_blocked
        "NOT_STARTED" -> R.string.trial_msg_idle
        "PREPARING" -> R.string.trial_msg_preparing
        "PREPARED" -> if (activePlan?.reset == true) R.string.profile_restore_prepared else R.string.trial_msg_prepared
        "APPLYING" -> if (activePlan?.reset == true) R.string.profile_restoring else R.string.trial_msg_applying
        "MODIFIER_REPORTED_SUCCESS" -> if (activePlan?.reset == true) R.string.profile_restore_reported else R.string.trial_msg_success
        "MODIFIER_REPORTED_FAILURE" -> R.string.trial_msg_failure
        "OPEN_CARRIER_FIRST" -> R.string.trial_msg_open
        "PENDING_UNKNOWN", "OUTCOME_UNCERTAIN", "OUTCOME_UNCERTAIN_OR_PRECHECK_FAILED" -> R.string.trial_msg_unknown
        "LEGACY_PREPARATION_NEEDS_REBOOT" -> R.string.trial_msg_reboot
        "REQUEST_NOT_TRACKED" -> if (phase == "prepare") R.string.trial_msg_reboot else R.string.trial_msg_unknown
        "PREPARATION_ENDED_AFTER_REBOOT" -> R.string.trial_msg_recovered
        "CHECKING" -> R.string.trial_msg_checking
        "PREVIOUS_ATTEMPT_RECORDED", "LOCKED" -> R.string.trial_msg_locked
        "TICKET_EXPIRED", "SNAPSHOT_EXPIRED" -> R.string.trial_msg_expired
        "SNAPSHOT_UNAVAILABLE", "PREPARE_FAILED", "PRECHECK_FAILED" -> R.string.trial_msg_precheck
        else -> R.string.trial_msg_other
    }
    }
    val canApply: Boolean get() = ticket != null && !busy && !locked
    val canInspect: Boolean get() = !busy && blocksReads && phase.isNotBlank()
    fun initialize(context: Context) {
        if (app != null) return
        app = context.applicationContext as android.app.Application
        val pref = preferences()
        locked = pref.getBoolean("locked", false)
        var saved = pref.getString("outcome", "NOT_STARTED") ?: "NOT_STARTED"
        phase = pref.getString("phase", "") ?: ""
        if (locked && phase.isEmpty()) {
            // 2.7 persisted this marker only before ConfigReader, never before ImsModifier.
            phase = if (saved == "PREPARING_INTERRUPTED") "prepare" else "apply"
            check(pref.edit().putString("phase", phase).putInt("boot", boot()).commit())
            saved = if (phase == "prepare") "LEGACY_PREPARATION_NEEDS_REBOOT" else "OUTCOME_UNCERTAIN"
        }
        activePlan = RegionPlanCodec.restore(pref.getString("plan", null))
        requestId = pref.getString("request", "") ?: ""
        code = if (locked && requestId.isEmpty() && phase == "prepare") "LEGACY_PREPARATION_NEEDS_REBOOT" else saved
        if (code == "PREPARING_INTERRUPTED" || code == "APPLY_INTENT_OUTCOME_UNKNOWN") code = "PENDING_UNKNOWN"
        detail = pref.getString("detail", null) ?: if (locked) saved else ""
        snapshotPath = pref.getString("snapshot", "") ?: ""
        blocksReads = locked && needsHold(code)
        if (blocksReads) ReaderSession.beginPrivateRead()
    }
    fun onVisible(context: Context) {
        initialize(context); visible = true
        schedulePoll()
    }
    fun onHidden() { visible = false }
    private fun schedulePoll() {
        if (polling || !visible || busy || !locked || requestId.isEmpty() || code != "PENDING_UNKNOWN") return
        polling = true
        main.postDelayed({ polling = false; if (visible && !busy && locked && code == "PENDING_UNKNOWN") inspect(app!!) }, 3_000)
    }
    private fun mapped(context: Context, plan: RegionPlan): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        val list = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList ?: return false
        return list.count { plan.matches(it.simSlotIndex, it.subscriptionId, it.mccString, it.mncString, it.isEmbedded) } == 1
    }
    private fun service(context: Context): ICarrierTrial? {
        val resetting = activePlan?.reset == true
        val direct = preferences().getBoolean("direct", false)
        val delegated = preferences().getString("backend", "") == "native-v2"
        val native = preferences().getString("backend", "") == "native-v1"
        val channel = if (delegated) "delegated" else if (native) "native" else if (direct) "direct" else if (resetting) "restore" else "legacy"
        bridge?.takeIf { bridgeChannel == channel && it.asBinder().pingBinder() }?.let { return it }
        val signal = CountDownLatch(1)
        main.post {
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    bridge = ICarrierTrial.Stub.asInterface(binder); bridgeChannel = channel; signal.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) { if (bridgeChannel == channel) bridge = null; signal.countDown() }
            }
            connection = conn
            try {
                Shizuku.bindUserService(Shizuku.UserServiceArgs(ComponentName(context, if (delegated) DelegatedCarrierService::class.java else if (native) NativeCarrierService::class.java else CarrierTrialService::class.java))
                    .tag(if (delegated) "sim-region-delegated-carrier" else if (native) "sim-region-native-carrier" else if (direct) "sim-region-carrier-direct" else if (resetting) "sim-region-carrier-restore" else "sim-region-a1-trial").version(if (delegated) 23 else if (native || direct || resetting) 1 else 3)
                    .daemon(false).debuggable(false).processNameSuffix(if (delegated) "delegatedcarrier" else if (native) "nativecarrier" else if (direct) "direct" else if (resetting) "restore" else "a1trial"), conn)
            } catch (_: RuntimeException) { signal.countDown() }
        }
        if (!signal.await(10, TimeUnit.SECONDS)) return null
        return bridge?.takeIf { bridgeChannel == channel && it.asBinder().pingBinder() }
    }

    private fun mark(outcome: String, operation: String, id: String, lock: Boolean, plan: RegionPlan? = null, direct: Boolean? = null) {
        val editor = preferences().edit().putBoolean("locked", lock).putString("phase", operation)
            .putString("request", id).putString("outcome", outcome).putInt("boot", boot())
        if (plan != null) editor.putString("plan", RegionPlanCodec.json(plan))
        if (direct != null) editor.putBoolean("direct", direct)
        check(editor.commit()) { "STORE_FAILED" }
    }    private fun accept(packet: Bundle, expectedId: String, expectedPhase: String, expectedPlan: RegionPlan): Bundle {
        if (preferences().getString("backend", "") == "native-v1" &&
            packet.getString("status") == "OUTCOME_UNCERTAIN" && packet.getString("detail") == "native:carrier-config:SecurityException") {
            packet.putString("status", "PRECHECK_FAILED")
        }
        val resultCode = packet.getString("status") ?: "INVALID_REPLY"
        if (resultCode == "REQUEST_NOT_TRACKED") return packet
        val supported = setOf("PREPARED", "PENDING_UNKNOWN", "LOCKED", "OUTCOME_UNCERTAIN", "PRECHECK_FAILED", "SNAPSHOT_UNAVAILABLE", "SNAPSHOT_EXPIRED", "OPEN_CARRIER_FIRST", "START_REJECTED", "CANCELLED", "MALFORMED_REPLY", "EMPTY_REPLY", "TICKET_EXPIRED", "MODIFIER_REPORTED_SUCCESS", "MODIFIER_REPORTED_FAILURE", "INVALID_MODIFIER_REPLY")
        if (resultCode !in supported) return Bundle().apply { putString("status", "INVALID_REPLY") }
        if (!TrialRecoveryRules.accepts(expectedId, expectedPhase, packet.getString("request_id"), packet.getString("phase")))
            return Bundle().apply { putString("status", "INVALID_REPLY") }
        if (RegionPlanCodec.decode(packet.getBundle("plan")) != expectedPlan) return Bundle().apply { putString("status", "INVALID_REPLY") }
        if(expectedPhase=="apply" && resultCode=="MODIFIER_REPORTED_SUCCESS") {
            val snapshot=apnFollowUp.take(expectedId,expectedPlan)
            if(snapshot!=null) {
                main.post { code="APN_RESTORING"; ReaderSession.notifyPrivateChange() }
                val apnResult=ApnSession.restoreAfterProfileChange(snapshot)
                main.post { autoApnCode=apnResult }
            }
        } else if(expectedPhase=="apply" && resultCode in RegionGuard.completedApply) apnFollowUp.take(expectedId,expectedPlan)
        if (expectedPhase == "prepare" && resultCode == "PREPARED") {
            val dump = requireNotNull(packet.getString("snapshot"))
            val token = requireNotNull(packet.getString("ticket"))
            check(dump.isNotBlank() && dump.length <= 400_000 && token.length == 36)
            val fileName = "region-before-$expectedId.txt"
            val atomic = AtomicFile(File(app!!.filesDir, fileName))
            val stream = atomic.startWrite()
            try {
                stream.write(("Selected subscription and requested profile: ${RegionPlanCodec.json(expectedPlan)}\nReceived=${java.util.Date()}\n" +
                    "Effective CarrierConfig snapshot only; not an exact override backup or automatic rollback.\n\n" + dump).toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (error: Exception) { atomic.failWrite(stream); throw error }
            check(preferences().edit().putBoolean("locked", false).putString("outcome", "PREPARED")
                .putString("snapshot", fileName).commit()) { "STORE_FAILED" }
            // Only the main thread owns the UI ticket. It is never persisted or applied here.
            return Bundle(packet).apply { putString("saved_snapshot", fileName) }
        }
        if (expectedPhase == "apply" && resultCode in RegionGuard.completedApply) {
            check(preferences().edit().putString("slot_${expectedPlan.slot}_plan", RegionPlanCodec.json(expectedPlan))
                .putString("slot_${expectedPlan.slot}_outcome", resultCode).putBoolean("slot_${expectedPlan.slot}_internet_verified", resultCode=="MODIFIER_REPORTED_SUCCESS" && packet.getString("detail").orEmpty().contains(":internet=")).putLong("slot_${expectedPlan.slot}_time", System.currentTimeMillis()).commit())
        }
        val hold = needsHold(resultCode)
        check(preferences().edit().putBoolean("locked", expectedPhase == "apply" || hold)
            .putString("outcome", resultCode).putString("detail", packet.getString("detail")).commit()) { "STORE_FAILED" }
        return packet
    }
    private fun runWork(context: Context, action: String, newPlan: RegionPlan? = null, restoreApn: Boolean = false) {
        initialize(context)
        if(action in setOf("prepare","apply")) return // Retired country/operator activation; inspect remains available.
        if (busy) return
        if (action == "direct" && !canApplyDirect(newPlan)) return
        if (action == "prepare" && (!canPrepare() || newPlan?.valid() != true || newPlan.configOnly)) return
        if (action == "apply" && (!canApply || ReaderSession.state.running)) return
        if (action == "inspect" && !canInspect) return
        val held = blocksReads
        if (!held && !ReaderSession.beginPrivateRead()) return
        if (action == "prepare" || action == "direct") activePlan = newPlan
        val oldTicket = ticket
        if (action != "inspect") ticket = null
        if(action=="direct") { apnFollowUp.clear(); autoApnCode=null }
        busy = true; code = when (action) { "prepare" -> "PREPARING"; "apply", "direct" -> "APPLYING"; else -> "CHECKING" }
        detail = ""; ReaderSession.notifyPrivateChange()
        worker.execute {
            val result = try {
                if (action == "prepare") mark("NOT_SENT", "prepare", "", false, requireNotNull(newPlan), direct = false)
                if (action == "direct") {
                    check(preferences().edit().putString("backend", "native-v2").commit())
                    mark("NOT_SENT", "apply", "", false, requireNotNull(newPlan), direct = true)
                }
                if (action == "inspect" && locked && TrialRecoveryRules.afterReboot(phase, preferences().getInt("boot", -1), boot())) {
                    // A reboot ended the old read-only process. Never clear any apply intent.
                    mark("PREPARATION_ENDED_AFTER_REBOOT", "", "", false)
                    Bundle().apply { putString("status", "PREPARATION_ENDED_AFTER_REBOOT") }
                } else if (action == "inspect" && requestId.isEmpty()) {
                    Bundle().apply { putString("status", if (phase == "prepare") "LEGACY_PREPARATION_NEEDS_REBOOT" else "REQUEST_NOT_TRACKED") }
                } else {
                    require(ReaderSession.accessError() == null) { "SHIZUKU_REQUIRED" }
                    if(action == "direct") {
                        val selected=requireNotNull(newPlan)
                        require(mapped(app!!,selected)) { "SELECTION_CHANGED" }
                        val ready=if(selected.reset || selected.configOnly) true else ApnSession.ensureBeforeProfileChange(app!!,selected) {
                            main.post { code="APN_BACKUP_SAVING"; ReaderSession.notifyPrivateChange() }
                        }
                        if(ready) main.post { code="APPLYING"; ReaderSession.notifyPrivateChange() }
                        check(ApnAutoSaveRules.mayChangeProfile(selected.reset || selected.configOnly,ready)) { "APN_BACKUP_FAILED" }
                    }
                    val remote = service(app!!) ?: error("BRIDGE_UNAVAILABLE")
                    val id: String
                    val operation: String
                    val packet: Bundle
                    when (action) {
                        "direct" -> {
                            val selected = requireNotNull(newPlan)
                            require(mapped(app!!, selected)) { "SELECTION_CHANGED" }
                            id = UUID.randomUUID().toString(); operation = "apply"
                            val frozen=if(restoreApn && !selected.reset && !selected.configOnly) ApnSession.snapshotForPlan(app!!,selected) else null
                            check(!restoreApn || selected.reset || selected.configOnly || frozen!=null) { "APN_BACKUP_FAILED" }
                            mark("APPLY_INTENT_OUTCOME_UNKNOWN", operation, id, true, selected, direct = true)
                            if(frozen!=null) apnFollowUp.arm(id,selected,frozen)
                            LocalCarrierAuthorization.arm(id, selected)
                            packet = remote.applyDirect(id, RegionPlanCodec.bundle(selected))
                        }
                        "prepare" -> {
                            val selected = requireNotNull(newPlan)
                            require(mapped(app!!, selected)) { "SELECTION_CHANGED" }
                            id = UUID.randomUUID().toString(); operation = "prepare"
                            mark("PREPARING_INTERRUPTED", operation, id, true, selected)
                            packet = remote.prepare(id, RegionPlanCodec.bundle(selected))
                        }
                        "apply" -> {
                            val selected = requireNotNull(activePlan)
                            require(mapped(app!!, selected)) { "SELECTION_CHANGED" }
                            val backup = File(app!!.filesDir, snapshotPath)
                            check(snapshotPath.startsWith("region-before-") && backup.isFile && backup.length() > 100)
                            id = requestId; operation = "apply"
                            mark("APPLY_INTENT_OUTCOME_UNKNOWN", operation, id, true, selected)
                            packet = remote.apply(requireNotNull(oldTicket))
                        }
                        else -> { id = requestId; operation = phase; packet = remote.inspect(id) }
                    }
                    accept(packet, id, operation, requireNotNull(if (action == "prepare" || action == "direct") newPlan else activePlan))
                }
            } catch (error: Exception) {
                val saved = preferences().getString("outcome", "") ?: ""
                val backupFailed=error.message=="APN_BACKUP_FAILED" && !needsHold(saved)
                if(backupFailed) mark("APN_BACKUP_FAILED","apply","",false,newPlan,direct=true)
                Bundle().apply {
                    putString("status", if(backupFailed) "APN_BACKUP_FAILED" else if (needsHold(saved) || action == "inspect") "INSPECT_FAILED" else "PRECHECK_FAILED")
                    putString("detail", error.javaClass.simpleName)
                }
            }
            main.post {
                val pref = preferences()
                locked = pref.getBoolean("locked", false)
                phase = pref.getString("phase", "") ?: ""; requestId = pref.getString("request", "") ?: ""
                activePlan = RegionPlanCodec.restore(pref.getString("plan", null))
                snapshotPath = pref.getString("snapshot", "") ?: ""
                code = result.getString("status") ?: "INVALID_REPLY"; detail = result.getString("detail") ?: ""
                ticket = if (code == "PREPARED" && !locked && phase == "prepare") result.getString("ticket") else null
                busy = false; releaseOrHold(); schedulePoll()
            }
        }
    }
    fun canAcknowledgeCarrierRefusal() = CarrierRefusalRules.canAcknowledge(busy,code,detail,activePlan)
    fun acknowledgeCarrierRefusal() {
        if(!canAcknowledgeCarrierRefusal()) return
        // Explicit user acknowledgement of a terminal returned refusal, without another device call.
        val previous=detail
        mark("CARRIER_REFUSAL_ACKNOWLEDGED","apply",requestId,false)
        code="CARRIER_REFUSAL_ACKNOWLEDGED"; detail=previous;locked=false
        preferences().edit().putString("detail",previous).apply()
        releaseOrHold()
    }
    fun canAcknowledgeLocalApnFailure():Boolean = !busy && BundledProfiles.isApply(activePlan?.configMode) &&
        code=="OUTCOME_UNCERTAIN" && detail.matches(Regex("native2:apn-shizuku:APN_READBACK_FAILED(?::(?:internet|mms|ims))?"))
    fun acknowledgeLocalApnFailure() {
        if(!canAcknowledgeLocalApnFailure()) return
        // A returned terminal helper failure, never a timeout or lost reply.
        mark("LOCAL_APN_FAILURE_ACKNOWLEDGED","apply",requestId,false)
        code="LOCAL_APN_FAILURE_ACKNOWLEDGED";detail="native2:apn-shizuku:failure-acknowledged";locked=false
        releaseOrHold()
    }
    fun canArchiveV1AfterReboot(): Boolean = !busy && blocksReads &&
        preferences().getString("backend", "") == "native-v1" &&
        preferences().getInt("boot", -1) >= 0 && boot() > preferences().getInt("boot", -1)
    fun archiveV1AfterReboot() {
        if (!canArchiveV1AfterReboot()) return
        // User acknowledges an unknown OLD outcome. This neither restores nor retries any write.
        mark("V1_ATTEMPT_ARCHIVED", "apply", requestId, false)
        code = "V1_ATTEMPT_ARCHIVED"; detail = "native:v1:archived-after-reboot"; locked = false
        releaseOrHold()
    }
    fun prepare(context: Context, plan: RegionPlan) = runWork(context, "prepare", plan)
    fun apply(context: Context, displayed: RegionPlan) {
        if (canApplyFor(displayed)) runWork(context, "apply")
    }
    fun applyDirect(context: Context, plan: RegionPlan, restoreApn: Boolean = false) = runWork(context, "direct", plan, restoreApn)
    fun inspect(context: Context) = runWork(context, "inspect")
}





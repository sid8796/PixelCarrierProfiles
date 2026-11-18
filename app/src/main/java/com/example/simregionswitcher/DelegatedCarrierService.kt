package com.example.simregionswitcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.telephony.SubscriptionInfo
import java.util.UUID
import java.util.concurrent.Executors

/** Starts our own foreground app instrumentation, not the external Carrier IMS process. */
class DelegatedCarrierService(private val context: Context) : ICarrierTrial.Stub() {
    private val appUid = context.applicationInfo.uid
    private val worker = Executors.newSingleThreadExecutor()
    private var request = ""
    private var selected: RegionPlan? = null
    private var pid = -1
    private var claimed = false
    private var identityEntered = false
    private var watcher: IBinder? = null
    @Volatile private var running = false
    @Volatile private var packet = status("REQUEST_NOT_TRACKED", "native2:idle")
    private fun caller() { check(Binder.getCallingUid() == appUid) { "UNEXPECTED_CALLER" } }
    private fun status(code: String, detail: String) = Bundle().apply { putString("status", code); putString("detail", detail) }
    private fun envelope() = Bundle(packet).apply {
        putString("request_id", request); putString("phase", "apply")
        selected?.let { putBundle("plan", RegionPlanCodec.bundle(it)) }
    }
    @Synchronized override fun applyDirect(id: String, rawPlan: Bundle): Bundle {
        caller()
        val plan = requireNotNull(RegionPlanCodec.decode(rawPlan))
        require(!plan.persistConfig) { "PERSISTENCE_REQUIRES_SYSTEM_APP" }
        require(BundledProfiles.canApply(plan) && UUID.fromString(id).toString() == id)
        when (DirectApplyRules.decide(id == request, selected == plan, true, running, request.isNotEmpty(), packet.getString("status"))) {
            DirectApplyRules.Decision.OBSERVE -> return envelope()
            DirectApplyRules.Decision.BLOCK -> return status("LOCKED", "native2:previous-request")
            DirectApplyRules.Decision.START -> Unit
        }
        request = id; selected = plan; pid = Binder.getCallingPid(); claimed = false; identityEntered = false
        running = true; packet = status("PENDING_UNKNOWN", "native2:starting")
        worker.execute { launch(plan) }
        return envelope()
    }
    @Synchronized override fun inspect(id: String): Bundle { caller(); return if (id == request && id.isNotEmpty()) envelope() else status("REQUEST_NOT_TRACKED", "native2:service-restarted") }
    override fun prepare(id: String, plan: Bundle): Bundle { caller(); return status("PRECHECK_FAILED", "native2:unsupported") }
    override fun apply(ticket: String): Bundle { caller(); return status("PRECHECK_FAILED", "native2:unsupported") }
    override fun destroy() { caller(); check(!running); worker.shutdown(); kotlin.system.exitProcess(0) }
    private fun rawService(name: String): IBinder = Class.forName("android.os.ServiceManager")
        .getMethod("getService", String::class.java).invoke(null, name) as? IBinder ?: error("BINDER_ABSENT")
    private fun mapping(plan: RegionPlan) {
        val service = PlatformTransportReader.service("isub", "com.android.internal.telephony.ISub\$Stub")
        val type = Class.forName("com.android.internal.telephony.ISub")
        val observed = type.getMethod("getSubId", Int::class.javaPrimitiveType).invoke(service, plan.slot)
        check(BinderMapping.assess(plan.subId, observed).error == null) { "MAPPING_CHANGED" }
        val prefix = listOf(Int::class.javaPrimitiveType, String::class.java, String::class.java)
        val method = type.methods.singleOrNull { it.name == "getActiveSubscriptionInfo" &&
            (it.parameterTypes.toList() == prefix || it.parameterTypes.toList() == prefix + Boolean::class.javaPrimitiveType) }
            ?: error("SUBSCRIPTION_API_UNAVAILABLE")
        val pkg = if (android.os.Process.myUid() == 2000) "com.android.shell" else context.packageName
        val args = if (method.parameterCount == 3) arrayOf(plan.subId, pkg, null) else arrayOf(plan.subId, pkg, null, false)
        val info = method.invoke(service, *args) as? SubscriptionInfo ?: error("SUBSCRIPTION_UNAVAILABLE")
        check(plan.matches(info.simSlotIndex, info.subscriptionId, info.mccString, info.mncString, info.isEmbedded)) { "SELECTION_CHANGED" }
    }
    private fun launch(plan: RegionPlan) {
        var dispatched = false
        try {
            mapping(plan)
            val config = rawService("carrier_config")
            val phone = if(plan.configOnly) null else PlatformTransportReader.service("phone", "com.android.internal.telephony.ITelephony\$Stub")
            val phoneType = Class.forName("com.android.internal.telephony.ITelephony")
            val setter = if(plan.configOnly) null else phoneType.getMethod("setCarrierTestOverride", Int::class.javaPrimitiveType, *Array(9) { String::class.java })
            val clear = if(plan.configOnly) null else phoneType.methods.singleOrNull { it.name == "clearCarrierTestOverride" && it.parameterTypes.toList() == listOf(Int::class.javaPrimitiveType) }
            val am = PlatformTransportReader.service("activity", "android.app.IActivityManager\$Stub")
            val amType = Class.forName("android.app.IActivityManager")
            val processes = amType.getMethod("getRunningAppProcesses").invoke(am) as? List<*>
            check(processes.orEmpty().filterIsInstance<ActivityManager.RunningAppProcessInfo>().any { it.uid == appUid && it.pid == pid && it.processName == context.packageName }) { "APP_PROCESS_CHANGED" }
            val gate = object : INativeCarrierGate.Stub() {
                private var apnsAttempted=false
                private var apnsVerified=false
                private var apnMillis=0L
                private fun authenticate(token: String) {
                    caller(); check(Binder.getCallingPid() == pid && token == request && running) { "INVALID_CAPABILITY" }
                }
                @Synchronized override fun claim(token: String): Bundle {
                    authenticate(token); check(!claimed); claimed = true
                    return Bundle().apply { putBoolean("accepted", true); putBinder("config", config) }
                }
                @Synchronized override fun progress(token: String, stage: String) {
                    authenticate(token); check(claimed && stage in setOf("carrier-config","apn-install") && !identityEntered)
                    packet = status("PENDING_UNKNOWN", "native2:$stage")
                }
                @Synchronized override fun installApns(token:String):Bundle {
                    authenticate(token)
                    check(claimed && !identityEntered && !apnsAttempted && BundledProfiles.isApply(plan.configMode))
                    apnsAttempted=true
                    val identity=Binder.clearCallingIdentity()
                    return try {
                        check(android.os.Process.myUid()==2000) { "SHELL_UID_REQUIRED" }
                        packet=status("PENDING_UNKNOWN","native2:apn-shizuku")
                        val started=android.os.SystemClock.elapsedRealtime()
                        val result=LocalPbApns.install(plan,appUid/100000) { mapping(plan) }
                        apnMillis=android.os.SystemClock.elapsedRealtime()-started
                        apnsVerified=result.getBoolean("ok")
                        result
                    } finally { Binder.restoreCallingIdentity(identity) }
                }
                @Synchronized override fun identity(token: String): Bundle {
                    authenticate(token); check(claimed && !identityEntered)
                    identityEntered = true
                    val identity = Binder.clearCallingIdentity()
                    return try {
                        mapping(plan)
                        if(plan.configOnly) {
                            if(BundledProfiles.isClear(plan.configMode)) return status("MODIFIER_REPORTED_SUCCESS","native2:local-pb:cleared")
                            check(apnsAttempted && apnsVerified) { "APN_NOT_VERIFIED" }
                            packet=status("PENDING_UNKNOWN","native2:internet-selection")
                            val started=android.os.SystemClock.elapsedRealtime()
                            val changed=LocalPbApns.selectInternet(plan,appUid/100000) { mapping(plan) }
                            val elapsed=android.os.SystemClock.elapsedRealtime()-started
                            return status("MODIFIER_REPORTED_SUCCESS","native2:local-pb:applied:apn=${apnMillis}ms:internet=${if(changed) "selected" else "already"}:select=${elapsed}ms")
                        }
                        packet = status("PENDING_UNKNOWN", "native2:carrier-identity")
                        if (plan.reset && clear != null) clear.invoke(phone, plan.subId)
                        else requireNotNull(setter).invoke(phone, plan.subId, if (plan.reset) plan.sourceMcc + plan.sourceMnc else plan.mcc + plan.mnc,
                            "", "", "", "", "", "", null, null)
                        status("MODIFIER_REPORTED_SUCCESS", if (plan.reset && clear == null) "native2:returned:original-identity-fallback" else "native2:returned")
                    } catch (error: Exception) {
                        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
                        val reason=cause.message?.takeIf { it in setOf("APN_SELECTION_FAILED","APN_CLEANUP_CHANGED","APN_CLEANUP_FAILED","APN_READBACK_FAILED","APN_NOT_UNIQUE","EXISTING_APN_CONFLICT","APN_TIMEOUT","APN_REPLY_INVALID","APN_REPLY_TOO_LARGE","APN_COMMAND_FAILED") }
                        status("OUTCOME_UNCERTAIN", if(BundledProfiles.isApply(plan.configMode) && reason!=null) "native2:internet-selection:$reason" else "native2:carrier-identity:${cause.javaClass.simpleName}")
                    } finally { Binder.restoreCallingIdentity(identity) }
                }
            }
            val component = ComponentName(context.packageName, CarrierProfileInstrumentation::class.java.name)
            val descriptor = "android.app.IInstrumentationWatcher"
            val sink = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == INTERFACE_TRANSACTION) { reply?.writeString(descriptor); return true }
                    if (code != FIRST_CALL_TRANSACTION && code != FIRST_CALL_TRANSACTION + 1) return super.onTransact(code, data, reply, flags)
                    // Watcher results are accepted only from Android's system server.
                    check(Binder.getCallingUid() == 1000)
                    data.enforceInterface(descriptor)
                    val name = data.readTypedObject(ComponentName.CREATOR)
                    val result = data.readInt()
                    val raw = data.readTypedObject(Bundle.CREATOR)
                    data.enforceNoDataAvail(); check(name == component)
                    if (code == FIRST_CALL_TRANSACTION + 1) {
                        synchronized(this@DelegatedCarrierService) {
                            val accepted = raw?.getString("status")
                            val detail = raw?.getString("detail")
                            packet = if (result == -1 && claimed && accepted in setOf("MODIFIER_REPORTED_SUCCESS", "PRECHECK_FAILED", "OUTCOME_UNCERTAIN") &&
                                detail != null && detail.startsWith("native2:") && detail.length <= 180 && (accepted != "MODIFIER_REPORTED_SUCCESS" || identityEntered))
                                status(accepted!!, detail) else status("OUTCOME_UNCERTAIN", "native2:instrumentation:invalid-result")
                            running = false; watcher = null
                        }
                    }
                    reply?.writeNoException(); return true
                }
            }
            watcher = sink
            val watcherType = Class.forName(descriptor)
            val proxy = Class.forName("$descriptor\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, sink)
            val connectionType = Class.forName("android.app.IUiAutomationConnection")
            val connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance()
            val start = amType.getMethod("startInstrumentation", ComponentName::class.java, String::class.java,
                Int::class.javaPrimitiveType, Bundle::class.java, watcherType, connectionType, Int::class.javaPrimitiveType, String::class.java)
            val arguments = Bundle().apply { putString("token", request); putInt("pid", pid); putBundle("plan", RegionPlanCodec.bundle(plan)); putBinder("gate", gate) }
            mapping(plan)
            dispatched = true
            val started = start.invoke(am, component, null, 8, arguments, proxy, connection, appUid / 100000, null) as Boolean
            if (!started) { packet = status("PRECHECK_FAILED", "native2:start:rejected"); running = false; watcher = null }
        } catch (error: Exception) {
            val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
            packet = status(if (dispatched) "OUTCOME_UNCERTAIN" else "PRECHECK_FAILED", "native2:start:${cause.javaClass.simpleName}")
            running = false
        }
    }
}

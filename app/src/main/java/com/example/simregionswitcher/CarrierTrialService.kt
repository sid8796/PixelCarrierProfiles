package com.example.simregionswitcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.os.UserHandle
import android.telephony.SubscriptionInfo
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Separate privileged writer with two fixed endpoints; no generic shell/Bundle API. */
class CarrierTrialService(private val context: Context) : ICarrierTrial.Stub() {
    private val appUid = context.applicationInfo.uid
    private var plan: RegionPlan? = null
    private var ticket: String? = null
    private var preparedAt = 0L
    private var attempted = false
    private var watcherRef: IBinder? = null
    private var unresolved = false
    private var requestId = ""
    private var phase = ""
    private var completion: TrialCompletion<Bundle>? = null
    private var finalPacket: Bundle? = null
    private fun envelope(packet: Bundle): Bundle = Bundle(packet).apply {
        putString("request_id", requestId); putString("phase", phase)
        plan?.let { putBundle("plan", RegionPlanCodec.bundle(it)) }
    }
    private fun cached(): Bundle? {
        val packet = finalPacket ?: return null
        if (packet.getString("status") == "PREPARED" && !A1TrialRules.ticketValid(ticket, ticket ?: "", preparedAt, SystemClock.elapsedRealtime())) {
            ticket = null; finalPacket = status("SNAPSHOT_EXPIRED")
        }
        return envelope(finalPacket!!)
    }
    private fun resolve(result: Bundle): Bundle {
        cached()?.let { return it }
        if (result.getString("status") == "PENDING_UNKNOWN") return envelope(result)
        unresolved = false; watcherRef = null
        val resolved = if (phase == "prepare") {
            if (result.getString("status") != "FINISHED") result
            else {
                val dump = result.getString("dump_text")
                if (dump.isNullOrBlank() || dump.length > 400_000 || !dump.contains("KEY_")) status("SNAPSHOT_UNAVAILABLE")
                else if (SystemClock.elapsedRealtime() - result.getLong("snapshot_elapsed", 0) > 300_000) status("SNAPSHOT_EXPIRED")
                else {
                    mapping()
                    val token = UUID.randomUUID().toString()
                    ticket = token; preparedAt = SystemClock.elapsedRealtime()
                    status("PREPARED").apply { putString("ticket", token); putString("snapshot", dump) }
                }
            }
        } else if (result.getString("status") != "FINISHED") result else {
            @Suppress("DEPRECATION") val value = result.get("result")
            status(when (value) { true -> "MODIFIER_REPORTED_SUCCESS"; false -> "MODIFIER_REPORTED_FAILURE"; else -> "INVALID_MODIFIER_REPLY" })
        }
        finalPacket = resolved
        return envelope(resolved)
    }
    @Synchronized override fun inspect(expectedId: String): Bundle {
        caller()
        if (expectedId.isBlank() || expectedId != requestId) return status("REQUEST_NOT_TRACKED")
        val identity = Binder.clearCallingIdentity()
        return try {
            cached()?.let { return it }
            completion?.peek()?.let { resolve(it) } ?: envelope(status("PENDING_UNKNOWN"))
        } catch (error: Exception) {
            val packet = failure(error)
            if (!unresolved) finalPacket = packet
            envelope(packet)
        }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    private fun status(code: String) = Bundle().apply { putString("status", code) }
    private fun caller() {
        if (Binder.getCallingUid() != appUid) throw SecurityException("Unexpected caller")
    }
    private fun target() {
        val pkg = context.packageManager.getPackageInfo(ReaderProtocol.PACKAGE,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_INSTRUMENTATION.toLong()))
        check(ReaderProtocol.supported(pkg.versionName, pkg.longVersionCode)) { "UNREVIEWED_VERSION" }
        check(pkg.applicationInfo?.enabled == true) { "TARGET_DISABLED" }
        for (name in listOf(A1TrialRules.CONFIG, A1TrialRules.MODIFIER)) {
            check(pkg.instrumentation.orEmpty().count { it.name == name && it.targetPackage == ReaderProtocol.PACKAGE } == 1) { "COMPONENT_MISMATCH" }
        }
    }
    private fun mapping() {
        val selected = requireNotNull(plan)
        val service = PlatformTransportReader.service("isub", "com.android.internal.telephony.ISub\$Stub")
        val type = Class.forName("com.android.internal.telephony.ISub")
        val observed = type.getMethod("getSubId", Int::class.javaPrimitiveType).invoke(service, selected.slot)
        check(BinderMapping.assess(selected.subId, observed).error == null) { "MAPPING_CHANGED" }
        // AOSP variants use (id, package, attributionTag[, boolean option]).
        // Accept only explicitly typed signatures; no guessed transaction IDs.
        val method = type.methods.singleOrNull { it.name == "getActiveSubscriptionInfo" &&
            (it.parameterTypes.toList() == listOf(Int::class.javaPrimitiveType, String::class.java, String::class.java) ||
             it.parameterTypes.toList() == listOf(Int::class.javaPrimitiveType, String::class.java, String::class.java, Boolean::class.javaPrimitiveType)) }
            ?: error("SUBSCRIPTION_API_UNAVAILABLE")
        val callingPackage = if (android.os.Process.myUid() == 2000) "com.android.shell" else context.packageName
        val args = if (method.parameterCount == 3) arrayOf(selected.subId, callingPackage, null)
            else arrayOf(selected.subId, callingPackage, null, false)
        val info = method.invoke(service, *args) as? SubscriptionInfo ?: error("SUBSCRIPTION_UNAVAILABLE")
        check(selected.matches(info.simSlotIndex, info.subscriptionId, info.mccString, info.mncString, info.isEmbedded)) { "SELECTION_CHANGED" }
    }
    @Synchronized override fun prepare(id: String, rawPlan: Bundle): Bundle {
        caller()
        if (unresolved || attempted && finalPacket?.getString("status") !in RegionGuard.completedApply) return envelope(status("LOCKED"))
        val selected = requireNotNull(RegionPlanCodec.decode(rawPlan)).also { require(!it.configOnly) }
        attempted = false; plan = selected
        require(id.matches(Regex("[0-9a-f-]{36}")))
        requestId = id; phase = "prepare"; ticket = null; finalPacket = null; completion = null
        val identity = Binder.clearCallingIdentity()
        return try {
            target(); mapping()
            resolve(invoke(A1TrialRules.CONFIG, Bundle().apply {
                putInt("select_sim_id", selected.subId); putBoolean("dump", true)
            }))
        } catch (error: Exception) {
            val packet = failure(error)
            if (!unresolved) finalPacket = packet
            envelope(packet)
        }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    @Synchronized override fun apply(received: String): Bundle {
        caller()
        if (attempted || unresolved) return envelope(status("LOCKED"))
        if (!A1TrialRules.ticketValid(ticket, received, preparedAt, SystemClock.elapsedRealtime())) {
            ticket = null; phase = "apply"; finalPacket = status("TICKET_EXPIRED")
            return envelope(finalPacket!!)
        }
        ticket = null; phase = "apply"; finalPacket = null; completion = null
        val selected = requireNotNull(plan)
        val identity = Binder.clearCallingIdentity()
        return try {
            target(); mapping(); attempted = true
            resolve(invoke(A1TrialRules.MODIFIER, Bundle().apply {
                RegionActionRules.modifierArguments(selected).forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        else -> error("INVALID_ACTION")
                    }
                }
            }))
        } catch (error: Exception) {
            val packet = failure(error)
            if (!unresolved) finalPacket = packet
            envelope(packet)
        }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    /** Confirmed app request; no ConfigReader or snapshot. Duplicate IDs never start again. */
    @Synchronized override fun applyDirect(id: String, rawPlan: Bundle): Bundle {
        caller()
        require(id.matches(Regex("[0-9a-f-]{36}")))
        val selected = requireNotNull(RegionPlanCodec.decode(rawPlan)).also { require(!it.configOnly) }
        val identity = Binder.clearCallingIdentity()
        return try {
            when (DirectApplyRules.decide(id == requestId, plan == selected, phase == "apply",
                unresolved, attempted, finalPacket?.getString("status"))) {
                DirectApplyRules.Decision.BLOCK -> return envelope(status("LOCKED"))
                DirectApplyRules.Decision.OBSERVE -> {
                    cached()?.let { return it }
                    return completion?.peek()?.let { resolve(it) } ?: envelope(status("PENDING_UNKNOWN"))
                }
                DirectApplyRules.Decision.START -> Unit
            }
            plan = selected; requestId = id; phase = "apply"
            ticket = null; completion = null; finalPacket = null; watcherRef = null; attempted = false
            try {
                target(); mapping(); attempted = true
                resolve(invoke(A1TrialRules.MODIFIER, Bundle().apply {
                    RegionActionRules.modifierArguments(selected).forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            else -> error("INVALID_ACTION")
                        }
                    }
                }))
            } catch (error: Exception) {
                val packet = failure(error)
                if (!unresolved) finalPacket = packet
                envelope(packet)
            }
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    private fun failure(error: Exception): Bundle {
        val root = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        val fixedCodes = setOf("UNREVIEWED_VERSION", "TARGET_DISABLED", "COMPONENT_MISMATCH", "MAPPING_CHANGED", "SUBSCRIPTION_API_UNAVAILABLE", "SUBSCRIPTION_UNAVAILABLE", "SELECTION_CHANGED")
        val code = root.message?.takeIf { it in fixedCodes } ?: root.javaClass.simpleName
        return status(if (attempted || unresolved) "OUTCOME_UNCERTAIN" else "PRECHECK_FAILED").apply { putString("detail", code) }
    }
    private fun invoke(className: String, arguments: Bundle): Bundle {
        val selected = requireNotNull(plan)
        check(A1TrialRules.allowedComponent(className))
        check(arguments.get("select_sim_id") is Int && arguments.getInt("select_sim_id") == selected.subId)
        val component = ComponentName(ReaderProtocol.PACKAGE, className)
        val amType = Class.forName("android.app.IActivityManager")
        val am = PlatformTransportReader.service(Context.ACTIVITY_SERVICE, "android.app.IActivityManager\$Stub")
        val target = context.packageManager.getApplicationInfo(ReaderProtocol.PACKAGE, 0)
        val processes = amType.getMethod("getRunningAppProcesses").invoke(am) as? List<*>
        if (processes?.filterIsInstance<ActivityManager.RunningAppProcessInfo>()?.none {
                it.uid == target.uid && it.processName == target.processName } != false) return status("OPEN_CARRIER_FIRST")
        val done = CountDownLatch(1)
        val response = TrialCompletion<Bundle>(requestId, phase)
        completion = response
        val descriptor = "android.app.IInstrumentationWatcher"
        val watcher = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) { reply?.writeString(descriptor); return true }
                if (code != FIRST_CALL_TRANSACTION && code != FIRST_CALL_TRANSACTION + 1) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(descriptor)
                val name = data.readTypedObject(ComponentName.CREATOR)
                val resultCode = data.readInt()
                val raw = data.readTypedObject(Bundle.CREATOR)
                check(name == component)
                if (code == FIRST_CALL_TRANSACTION + 1) {
                    val result = try {
                    val packet = status(if (resultCode == -1) "FINISHED" else "CANCELLED")
                    if (className == A1TrialRules.CONFIG) packet.putString("dump_text", raw?.getString("dump_text"))
                    else {
                        @Suppress("DEPRECATION") val value = raw?.get("result")
                        if (value is Boolean) packet.putBoolean("result", value)
                    }
                    packet.putLong("snapshot_elapsed", SystemClock.elapsedRealtime())
                    packet
                    } catch (_: RuntimeException) { status("MALFORMED_REPLY") }
                    response.finish(result); done.countDown()
                }
                reply?.writeNoException(); return true
            }
        }
        watcherRef = watcher
        val watcherType = Class.forName(descriptor)
        val proxy = Class.forName("$descriptor\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, watcher)
        val connectionType = Class.forName("android.app.IUiAutomationConnection")
        val connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance()
        val userId = UserHandle::class.java.getDeclaredMethod("getUserId", Int::class.javaPrimitiveType).invoke(null, appUid) as Int
        val start = amType.getMethod("startInstrumentation", ComponentName::class.java, String::class.java,
            Int::class.javaPrimitiveType, Bundle::class.java, watcherType, connectionType, Int::class.javaPrimitiveType, String::class.java)
        mapping() // Recheck immediately before crossing the IPC boundary.
        // Retain the pending lock on exceptions after crossing the mutation boundary.
        unresolved = true
        val started = start.invoke(am, component, null, 8, arguments, proxy, connection, userId, null) as Boolean
        if (!started) { unresolved = false; watcherRef = null; return status("START_REJECTED") }
        if (!done.await(40, TimeUnit.SECONDS)) return status("PENDING_UNKNOWN")
        unresolved = false; watcherRef = null
        return response.peek() ?: status("EMPTY_REPLY")
    }
    override fun destroy() { caller(); kotlin.system.exitProcess(0) }
}







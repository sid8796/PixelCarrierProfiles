package com.example.simregionswitcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.UserHandle
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Shizuku user service. It has exactly two read methods, no arbitrary command API. */
class ReaderUserService(private val context: Context) : IReadOnlyBridge.Stub() {
    private val appUid = context.applicationInfo.uid
    private val active = AtomicBoolean(false)
    private var activeWatcher: IBinder? = null

    override fun readIms(logicalSlot: Int, subscriptionId: Int, requestId: Long, callback: IReaderCallback): Bundle =
        read(ReadOperation.IMS, logicalSlot, subscriptionId, requestId, callback)

    override fun readConfig(logicalSlot: Int, subscriptionId: Int, requestId: Long, callback: IReaderCallback): Bundle =
        read(ReadOperation.CONFIG, logicalSlot, subscriptionId, requestId, callback)

    override fun readImsNumber(logicalSlot: Int, subscriptionId: Int): Bundle {
        if (Binder.getCallingUid() != appUid) throw SecurityException("Unexpected caller")
        fun unavailable(code: String) = Bundle().apply {
            putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL); putString("number_status", code)
        }
        if (logicalSlot !in 0..1 || subscriptionId < 0) return unavailable("MAPPING_CHANGED")
        if (!active.compareAndSet(false, true)) return unavailable("READ_ERROR")
        val identity = Binder.clearCallingIdentity()
        var keepLocked = false
        try {
            val result = PlatformImsNumberReader.read(logicalSlot, subscriptionId)
            keepLocked = result.getString("number_status") == "CLEANUP_FAILED"
            return result
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            return unavailable(when (cause) {
                is SecurityException -> "ACCESS_DENIED"
                is NoSuchMethodException, is NoSuchFieldException, is ClassNotFoundException -> "API_UNAVAILABLE"
                else -> "READ_ERROR"
            })
        } finally {
            if (!keepLocked) active.set(false)
            Binder.restoreCallingIdentity(identity)
        }
    }
    override fun readCapabilities(logicalSlot: Int, subscriptionId: Int): Bundle =
        readPlatform(logicalSlot, subscriptionId, true)

    override fun readTransport(logicalSlot: Int, subscriptionId: Int): Bundle =
        readPlatform(logicalSlot, subscriptionId, false)

    private fun readPlatform(logicalSlot: Int, subscriptionId: Int, capabilities: Boolean): Bundle {
        if (Binder.getCallingUid() != appUid) throw SecurityException("Unexpected caller")
        if (logicalSlot !in 0..1 || subscriptionId < 0) return failure("INVALID_SELECTION")
        if (!active.compareAndSet(false, true)) return failure("REMOTE_REQUEST_PENDING")
        val identity = Binder.clearCallingIdentity()
        try {
            return PlatformTransportReader.read(logicalSlot, subscriptionId, capabilities)
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            val code = when (cause) {
                is SecurityException -> "PLATFORM_ACCESS_DENIED"
                is NoSuchMethodException, is ClassNotFoundException -> "PLATFORM_API_UNAVAILABLE"
                else -> if (cause.javaClass.name == "android.os.ServiceSpecificException") "PLATFORM_SERVICE_UNAVAILABLE" else "PLATFORM_READ_ERROR"
            }
            return failure(code, "source=Android ITelephony; exception=${cause.javaClass.simpleName}")
        } finally {
            // One-shot getters have no persistent observer or delegated identity to unregister.
            // A timed-out integer callback may arrive later, but cannot update another request.
            active.set(false)
            Binder.restoreCallingIdentity(identity)
        }
    }
    private fun read(operation: ReadOperation, slot: Int, subId: Int, requestId: Long, callback: IReaderCallback): Bundle {
        if (Binder.getCallingUid() != appUid) throw SecurityException("Unexpected caller")
        if (slot !in 0..1 || subId < 0) return failure("INVALID_SELECTION")
        if (!active.compareAndSet(false, true)) return failure("REMOTE_REQUEST_PENDING")
        val identity = Binder.clearCallingIdentity()
        var invocationAttempted = false
        var phase = "target_package"
        try {
            ReaderTargetGate.check(context, operation)?.let {
                active.set(false); return failure(it, "phase=helper; step=target_package")
            }
            // A Shizuku UserService is not an Android app process. Do not use
            // SubscriptionManager static caches/default TelephonyManager here.
            phase = "subscription_binder"
            val manager = Class.forName("android.os.ServiceManager")
            val subscriptionBinder = manager.getMethod("getService", String::class.java)
                .invoke(null, "isub") as? IBinder
            if (subscriptionBinder == null) {
                active.set(false); return failure("REMOTE_MAPPING_UNAVAILABLE", "phase=helper; source=isub; binder absent")
            }
            val subStub = Class.forName("com.android.internal.telephony.ISub\$Stub")
            val subscriptionService = subStub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, subscriptionBinder)
            val rawMapping = Class.forName("com.android.internal.telephony.ISub")
                .getMethod("getSubId", Int::class.javaPrimitiveType).invoke(subscriptionService, slot)
            val evidence = BinderMapping.assess(subId, rawMapping)
            if (evidence.error != null) {
                active.set(false)
                return failure(evidence.error, "phase=helper; source=ISub.getSubId; slot=$slot; expected=$subId; observed=${evidence.actualSubId ?: "unknown"}")
            }
            phase = "reader_setup"
            // The context is package-scoped; use the selected app user, never hard-code user 0.
            val userId = UserHandle::class.java.getDeclaredMethod("getUserId", Int::class.javaPrimitiveType)
                .invoke(null, appUid) as Int
            val component = ComponentName(ReaderProtocol.PACKAGE, operation.className)
            val args = Bundle().apply {
                putInt("select_sim_id", subId)
                if (operation == ReadOperation.CONFIG) putStringArray("keys", ReaderProtocol.keys.toTypedArray())
            }
            val completed = CountDownLatch(1)
            val response = AtomicReference<Bundle>()
            val watcher = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == IBinder.INTERFACE_TRANSACTION) {
                        reply?.writeString(WATCHER_DESCRIPTOR); return true
                    }
                    if (code != IBinder.FIRST_CALL_TRANSACTION && code != IBinder.FIRST_CALL_TRANSACTION + 1) {
                        return super.onTransact(code, data, reply, flags)
                    }
                    data.enforceInterface(WATCHER_DESCRIPTOR)
                    val name = data.readTypedObject(ComponentName.CREATOR)
                    val resultCode = data.readInt()
                    if (name != component) throw SecurityException("Unexpected reader component")
                    if (code == IBinder.FIRST_CALL_TRANSACTION + 1) {
                        val result = try {
                            val raw = data.readTypedObject(Bundle.CREATOR)
                            Bundle().apply {
                                putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL)
                                putLong("completed_at", System.currentTimeMillis())
                                putInt("result_code", resultCode)
                                putBundle("payload", sanitize(operation, raw))
                            }
                        } catch (_: RuntimeException) { failure("MALFORMED_REPLY") }
                        // These readers release their delegated permission before finish().
                        response.set(result)
                        active.set(false)
                        activeWatcher = null
                        completed.countDown()
                        // One-way delivery also reaches an app which already received a timeout.
                        // A dead app callback never restarts or cancels the target reader.
                        runCatching { callback.onResult(requestId, result) }
                    } else {
                        // Consume and discard progress: only a final reply can produce a known result.
                        data.readTypedObject(Bundle.CREATOR)
                    }
                    reply?.writeNoException()
                    return true
                }
            }
            activeWatcher = watcher // Keep the callback alive even when the local wait expires.
            val watcherClass = Class.forName("android.app.IInstrumentationWatcher")
            val watcherStub = Class.forName("android.app.IInstrumentationWatcher\$Stub")
            val watcherProxy = watcherStub.getMethod("asInterface", IBinder::class.java).invoke(null, watcher)
            val connectionType = Class.forName("android.app.IUiAutomationConnection")
            val connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance()
            phase = "activity_binder"
            val activityBinder = manager.getMethod("getService", String::class.java)
                .invoke(null, Context.ACTIVITY_SERVICE) as? IBinder
            if (activityBinder == null) {
                active.set(false); activeWatcher = null
                return failure("ACTIVITY_SERVICE_UNAVAILABLE", "phase=helper; step=activity_binder")
            }
            val amClass = Class.forName("android.app.IActivityManager")
            val am = Class.forName("android.app.IActivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, activityBinder)
            if (am == null) {
                active.set(false); activeWatcher = null
                return failure("ACTIVITY_SERVICE_UNAVAILABLE", "phase=helper; step=activity_interface")
            }
            phase = "target_process"
            val targetInfo = context.packageManager.getApplicationInfo(ReaderProtocol.PACKAGE, 0)
            // NO_RESTART requires an existing target process. AOSP dereferences its
            // ProcessRecord without a null check. Never drop flag 8 as a fallback.
            val processes = amClass.getMethod("getRunningAppProcesses").invoke(am) as? List<*>
            val running = processes?.filterIsInstance<ActivityManager.RunningAppProcessInfo>()
                ?.any { it.uid == targetInfo.uid && it.processName == targetInfo.processName }
            if (running != true) {
                active.set(false); activeWatcher = null
                return failure(if (processes == null) "TARGET_PROCESS_UNKNOWN" else "TARGET_NOT_RUNNING",
                    "phase=helper; step=target_process; open Carrier IMS, wait for its screen, then return and read again")
            }
            val method = Class.forName("android.app.IActivityManager").getMethod(
                "startInstrumentation", ComponentName::class.java, String::class.java,
                Int::class.javaPrimitiveType, Bundle::class.java, watcherClass, connectionType,
                Int::class.javaPrimitiveType, String::class.java)
            phase = "reader_start"
            invocationAttempted = true
            val started = method.invoke(am, component, null, 8, args, watcherProxy, connection, userId, null) as Boolean
            if (!started) {
                active.set(false); activeWatcher = null; return failure("START_REJECTED")
            }
            phase = "reader_wait"
            if (!completed.await(15, TimeUnit.SECONDS)) {
                // Do not unlock/retry: the remote reader may still be running.
                return failure("READER_TIMEOUT_PENDING").apply { putBoolean("pending", true) }
            }
            return response.get() ?: failure("EMPTY_REPLY")
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            val knownRejected = cause is SecurityException || cause is IllegalArgumentException
            if (!invocationAttempted || knownRejected) { active.set(false); activeWatcher = null }
            return failure(if (knownRejected) "ACCESS_OR_ARGUMENT_REJECTED" else "TRANSPORT_ERROR", "phase=helper; step=$phase; exception=${cause.javaClass.simpleName}; origin=${if (error is InvocationTargetException) "invoked_method" else "local"}; frames=${cause.stackTrace.take(5).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }}; pending=${active.get()}").apply { putBoolean("pending", invocationAttempted && active.get()) }
        } finally { Binder.restoreCallingIdentity(identity) }
    }

    @Suppress("DEPRECATION")
    private fun sanitize(operation: ReadOperation, raw: Bundle?): Bundle? {
        if (raw == null) return null
        val clean = Bundle()
        if (raw.containsKey("result_msg")) {
            clean.putString("result_msg", (raw.get("result_msg") as? String)?.take(250) ?: "Invalid error payload")
        }
        if (raw.containsKey("result")) {
            val value = raw.get("result")
            when {
                operation == ReadOperation.IMS && value is Boolean -> clean.putBoolean("result", value)
                operation == ReadOperation.CONFIG && value is Bundle -> {
                    val values = Bundle()
                    for (key in ReaderProtocol.keys) {
                        val flag = value.get(key)
                        if (flag is Boolean) values.putBoolean(key, flag)
                        // Wrong/missing types remain Unknown, never false.
                    }
                    clean.putBundle("result", values)
                }
                else -> clean.putString("result", "INVALID_RESULT_TYPE")
            }
        }
        return clean
    }

    override fun destroy() {
        val uid = Binder.getCallingUid()
        if (uid != appUid && uid != 0 && uid != 2000) throw SecurityException("Unexpected caller")
        // Only our helper process exits; this never stops Carrier IMS or telephony.
        kotlin.system.exitProcess(0)
    }

    private fun failure(code: String, detail: String? = null) = Bundle().apply {
        putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL)
        putString("transport_error", code)
        detail?.let { putString("detail", it) }
    }

    companion object { private const val WATCHER_DESCRIPTOR = "android.app.IInstrumentationWatcher" }
}



package com.example.simregionswitcher

import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Temporarily observes existing MmTel associated URIs. Sends no SIP/network registration. */
object PlatformImsNumberReader {
    private const val DESCRIPTOR = "android.telephony.ims.aidl.IImsRegistrationCallback"
    private var retainedCallback: IBinder? = null // Disabled callback only if removal failed.
    private fun registered(snapshot: Bundle): Boolean? {
        if (snapshot.containsKey("transport_error")) return null
        val raw = snapshot.getBundle("payload")?.getBundle("result") ?: return null
        @Suppress("DEPRECATION")
        val decoded = TransportRules.decode(raw.get("state_before"), raw.get("state_after"), raw.get("transport"))
        return if (decoded.error != null) null else decoded.transport?.state == 2
    }
    fun read(slot: Int, subId: Int): Bundle {
        val before = registered(PlatformTransportReader.read(slot, subId))
        if (before == null) return packet("READ_ERROR")
        if (!before) return packet("NOT_REGISTERED")
        val phone = PlatformTransportReader.service("phone", "com.android.internal.telephony.ITelephony\$Stub")
        val phoneClass = Class.forName("com.android.internal.telephony.ITelephony")
        val callbackClass = Class.forName(DESCRIPTOR)
        val stub = Class.forName("$DESCRIPTOR\$Stub")
        fun transaction(name: String): Int = stub.getDeclaredField("TRANSACTION_$name").apply { isAccessible = true }.getInt(null)
        val uriCode = transaction("onSubscriberAssociatedUriChanged")
        val changing = setOf(transaction("onRegistering"), transaction("onDeregistered")) +
            listOfNotNull(runCatching { transaction("onDeregisteredWithDetails") }.getOrNull())
        val known = changing + setOf(uriCode, transaction("onRegistered"), transaction("onTechnologyChangeFailed"))
        val guard = Any()
        val ready = CountDownLatch(1)
        val enabled = AtomicBoolean(true)
        val changed = AtomicBoolean(false)
        val malformed = AtomicBoolean(false)
        val numbers = AtomicReference<List<String>?>(null)
        val uriSeen = AtomicBoolean(false)
        val observer = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
                if (code !in known) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(DESCRIPTOR)
                if (!enabled.get()) return true
                if (code in changing) { changed.set(true); ready.countDown() }
                if (code == uriCode) {
                    try {
                        val values = data.createTypedArray(Uri.CREATOR)
                        data.enforceNoDataAvail()
                        uriSeen.set(values != null && values.isNotEmpty())
                        val parsed = ImsNumberRules.fromUris(values?.take(32)?.map { it.toString() }.orEmpty())
                        synchronized(guard) { if (enabled.get()) numbers.set(parsed) }
                    } catch (_: RuntimeException) { malformed.set(true) }
                    ready.countDown()
                }
                return true // Callback AIDL is one-way. Other events contain no data we need.
            }
        }
        val proxy = stub.getMethod("asInterface", IBinder::class.java).invoke(null, observer)
        // Require both methods before attaching any observer.
        val attach = phoneClass.getMethod("registerImsRegistrationCallback", Int::class.javaPrimitiveType, callbackClass)
        val detach = phoneClass.getMethod("unregisterImsRegistrationCallback", Int::class.javaPrimitiveType, callbackClass)
        var attached = false
        var knownRejected = false
        var attempted = false
        var cleanupFailed = false
        var result = packet("READ_ERROR")
        try {
            attempted = true
            attach.invoke(phone, subId, proxy)
            attached = true
            val received = ready.await(5, TimeUnit.SECONDS)
            val after = registered(PlatformTransportReader.read(slot, subId))
            val decision = ImsNumberRules.decide(after, changed.get(), received, malformed.get(),
                uriSeen.get(), numbers.get().orEmpty())
            result = packet(decision.status, decision.numbers)
        } catch (error: Exception) {
            val cause = if (error is java.lang.reflect.InvocationTargetException) error.targetException else error
            knownRejected = !attached && (cause is SecurityException || cause is IllegalArgumentException)
            result = packet(if (cause is SecurityException) "ACCESS_DENIED" else "READ_ERROR")
        } finally {
            synchronized(guard) { enabled.set(false); numbers.set(null) }
            if (attempted && !knownRejected) {
                // Retry removal once for this exact observer only; never reset IMS.
                cleanupFailed = runCatching { detach.invoke(phone, subId, proxy) }.isFailure &&
                    runCatching { detach.invoke(phone, subId, proxy) }.isFailure
                if (cleanupFailed) retainedCallback = observer
            }
            if (cleanupFailed) result = packet("CLEANUP_FAILED")
        }
        return result
    }
    private fun packet(status: String, values: List<String> = emptyList()) = Bundle().apply {
        putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL)
        putString("number_status", status)
        if (status == "FOUND") putStringArrayList("numbers", ArrayList(values))
    }
}

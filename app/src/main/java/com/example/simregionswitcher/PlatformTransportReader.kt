package com.example.simregionswitcher

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A finite set of read-only ITelephony queries in the Shizuku shell/root process. */
object PlatformTransportReader {
    private const val CONSUMER = "com.android.internal.telephony.IIntegerConsumer"
    private class Consumer : Binder() {
        val value = AtomicReference<Int?>()
        val ready = CountDownLatch(1)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) { reply?.writeString(CONSUMER); return true }
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CONSUMER)
            val result = data.readInt()
            data.enforceNoDataAvail()
            if (value.compareAndSet(null, result)) ready.countDown()
            return true // IIntegerConsumer.accept is one-way; no synchronous reply.
        }
    }

    internal fun service(name: String, stub: String): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, name) as? IBinder ?: throw IllegalStateException("BINDER_ABSENT")
        return Class.forName(stub).getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: throw IllegalStateException("INTERFACE_ABSENT")
    }

    private fun mapping(slot: Int, subId: Int): BinderMappingEvidence {
        val sub = service("isub", "com.android.internal.telephony.ISub\$Stub")
        val raw = Class.forName("com.android.internal.telephony.ISub")
            .getMethod("getSubId", Int::class.javaPrimitiveType).invoke(sub, slot)
        return BinderMapping.assess(subId, raw)
    }

    fun read(slot: Int, subId: Int, includeCapabilities: Boolean = false): Bundle {
        val beforeMapping = mapping(slot, subId)
        beforeMapping.error?.let { return failure(it) }
        val phone = service(Context.TELEPHONY_SERVICE, "com.android.internal.telephony.ITelephony\$Stub")
        val type = Class.forName("com.android.internal.telephony.ITelephony")
        val consumerType = Class.forName(CONSUMER)
        val consumerStub = Class.forName("$CONSUMER\$Stub")
        fun query(method: String): Int? {
            val consumer = Consumer()
            val proxy = consumerStub.getMethod("asInterface", IBinder::class.java).invoke(null, consumer)
            // Only two fixed getter names below reach this local helper.
            type.getMethod(method, Int::class.javaPrimitiveType, consumerType).invoke(phone, subId, proxy)
            if (!consumer.ready.await(4, TimeUnit.SECONDS)) return null
            return consumer.value.get()
        }
        val before = query("getImsMmTelRegistrationState") ?: return failure("PLATFORM_REPLY_TIMEOUT")
        val transport = if (before == 2)
            query("getImsMmTelRegistrationTransportType") ?: return failure("PLATFORM_REPLY_TIMEOUT") else -1
        val capabilityValues = Bundle()
        if (includeCapabilities) {
            for (probe in CapabilityRules.probes) {
                try {
                    val raw = type.getMethod("isAvailable", Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .invoke(phone, subId, probe.capability, probe.technology)
                    if (raw is Boolean) capabilityValues.putBoolean(probe.key, raw)
                    else capabilityValues.putString("${probe.key}_error", "INVALID_RESULT_TYPE")
                } catch (error: Exception) {
                    val cause = if (error is java.lang.reflect.InvocationTargetException) error.targetException else error
                    capabilityValues.putString("${probe.key}_error", cause.javaClass.simpleName)
                }
            }
        }
        val after = query("getImsMmTelRegistrationState") ?: return failure("PLATFORM_REPLY_TIMEOUT")
        mapping(slot, subId).error?.let { return failure(it) }
        // Queries are sequential, not atomic. State bracketing detects some changes,
        // but cannot prove that no handover occurred between callbacks.
        return Bundle().apply {
            putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL)
            putInt("result_code", -1)
            putLong("completed_at", System.currentTimeMillis())
            putBundle("payload", Bundle().apply {
                putBundle("result", Bundle().apply {
                    putInt("state_before", before); putInt("state_after", after); putInt("transport", transport)
                    if (includeCapabilities) putAll(capabilityValues)
                })
            })
        }
    }

    private fun failure(code: String) = Bundle().apply {
        putInt("bridge_protocol", ReaderProtocol.BRIDGE_PROTOCOL)
        putString("transport_error", code)
        putString("detail", "source=Android ITelephony; step=transport_snapshot")
    }
}

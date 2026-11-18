package com.example.simregionswitcher

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.SubscriptionInfo
import java.util.UUID
import java.util.concurrent.Executors

/** Own shell-identity Shizuku service. No external APK, instrumentation or permission delegation. */
class NativeCarrierService(private val context: Context) : ICarrierTrial.Stub() {
    private val appUid = context.applicationInfo.uid
    private val worker = Executors.newSingleThreadExecutor()
    private var request = ""
    private var selected: RegionPlan? = null
    @Volatile private var packet = status("REQUEST_NOT_TRACKED", "native:idle")
    @Volatile private var running = false
    private fun caller() { check(Binder.getCallingUid() == appUid) { "UNEXPECTED_CALLER" } }
    private fun envelope() = Bundle(packet).apply {
        putString("request_id", request); putString("phase", "apply")
        selected?.let { putBundle("plan", RegionPlanCodec.bundle(it)) }
    }
    @Synchronized override fun applyDirect(id: String, rawPlan: Bundle): Bundle {
        caller()
        val plan = requireNotNull(RegionPlanCodec.decode(rawPlan)).also { require(!it.configOnly) }
        require(!plan.configOnly && plan.valid() && UUID.fromString(id).toString() == id)
        when (DirectApplyRules.decide(id == request, selected == plan, true, running,
            request.isNotEmpty(), packet.getString("status"))) {
            DirectApplyRules.Decision.OBSERVE -> return envelope()
            DirectApplyRules.Decision.BLOCK -> return status("LOCKED", "native:previous-request")
            DirectApplyRules.Decision.START -> Unit
        }
        request = id; selected = plan; running = true
        packet = status("PENDING_UNKNOWN", "native:preflight")
        worker.execute { execute(plan) }
        return envelope()
    }
    @Synchronized override fun inspect(id: String): Bundle {
        caller()
        return if (id == request && id.isNotEmpty()) envelope() else status("REQUEST_NOT_TRACKED", "native:service-restarted")
    }
    override fun prepare(id: String, plan: Bundle): Bundle { caller(); return status("PRECHECK_FAILED", "native:unsupported") }
    override fun apply(ticket: String): Bundle { caller(); return status("PRECHECK_FAILED", "native:unsupported") }
    override fun destroy() { caller(); check(!running); worker.shutdown(); kotlin.system.exitProcess(0) }

    private fun execute(plan: RegionPlan) {
        var fallback = false
        val port = object : NativeCarrierTransaction.Port {
            lateinit var phone: Any
            lateinit var config: Any
            lateinit var configMethod: java.lang.reflect.Method
            lateinit var setter: java.lang.reflect.Method
            var clear: java.lang.reflect.Method? = null
            override fun preflight() {
                phone = PlatformTransportReader.service("phone", "com.android.internal.telephony.ITelephony\$Stub")
                val phoneType = Class.forName("com.android.internal.telephony.ITelephony")
                config = PlatformTransportReader.service("carrier_config", "com.android.internal.telephony.ICarrierConfigLoader\$Stub")
                configMethod = Class.forName("com.android.internal.telephony.ICarrierConfigLoader").getMethod(
                    "overrideConfig", Int::class.javaPrimitiveType, PersistableBundle::class.java, Boolean::class.javaPrimitiveType)
                setter = phoneType.getMethod("setCarrierTestOverride", Int::class.javaPrimitiveType, *Array(9) { String::class.java })
                clear = phoneType.methods.singleOrNull { it.name == "clearCarrierTestOverride" &&
                    it.parameterTypes.toList() == listOf(Int::class.javaPrimitiveType) }
            }
            override fun mapping() = mapping(plan)
            override fun overrideConfig(reset: Boolean) {
                configMethod.invoke(config, plan.subId, if (reset) null else PersistableBundle(), false)
            }
            override fun identity(reset: Boolean) {
                if (reset && clear != null) clear!!.invoke(phone, plan.subId)
                else {
                    fallback = reset
                    setter.invoke(phone, plan.subId,
                        if (reset) plan.sourceMcc + plan.sourceMnc else plan.mcc + plan.mnc,
                        "", "", "", "", "", "", null, null)
                }
            }
        }
        val outcome = NativeCarrierTransaction.execute(plan.reset, port) {
            packet = status("PENDING_UNKNOWN", "native:$it")
        }
        packet = status(outcome.code, "native:${outcome.stage}" +
            if (outcome.error != null) ":${outcome.error}" else if (fallback) ":original-identity-fallback" else "")
        running = false
    }
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
        val args = if (method.parameterCount == 3) arrayOf(plan.subId, pkg, null)
            else arrayOf(plan.subId, pkg, null, false)
        val info = method.invoke(service, *args) as? SubscriptionInfo ?: error("SUBSCRIPTION_UNAVAILABLE")
        check(plan.matches(info.simSlotIndex, info.subscriptionId, info.mccString, info.mncString, info.isEmbedded)) { "SELECTION_CHANGED" }
    }
    companion object {
        private fun status(code: String, detail: String) = Bundle().apply {
            putString("status", code); putString("detail", detail)
        }
    }
}

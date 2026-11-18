package com.example.simregionswitcher

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.os.IBinder
import org.lsposed.hiddenapibypass.LSPass
import android.telephony.SubscriptionManager

/** App UID performs CarrierConfig writes with temporary shell permission delegation. */
class CarrierProfileInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle) { this.arguments = Bundle(arguments); start() }
    override fun onStart() {
        var entered = false
        var stage = "authorization"
        var automation: UiAutomation? = null
        var code = "PRECHECK_FAILED"
        var detail = ""
        try {
            val token = requireNotNull(arguments.getString("token"))
            check(arguments.getInt("pid", -1) == Process.myPid()) { "PROCESS_CHANGED" }
            val plan = requireNotNull(RegionPlanCodec.decode(arguments.getBundle("plan")))
            check(BundledProfiles.canApply(plan)) { "PROFILE_VERSION_NOT_SUBMITTABLE" }
            check(!plan.persistConfig) { "PERSISTENCE_REQUIRES_SYSTEM_APP" }
            check(LocalCarrierAuthorization.consume(token, plan)) { "NOT_ARMED" }
            val gate = INativeCarrierGate.Stub.asInterface(arguments.getBinder("gate")) ?: error("GATE_ABSENT")
            val claim = gate.claim(token)
            check(claim.getBoolean("accepted")) { "GATE_REJECTED" }
            stage = "hidden-api"
            check(LSPass.setHiddenApiExemptions("")) { "HIDDEN_API_UNAVAILABLE" }
            val configBinder = requireNotNull(claim.getBinder("config"))
            stage = "delegation"
            TemporaryShellPermissionLease.run(acquire = {
                automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                automation!!.adoptShellPermissionIdentity(android.Manifest.permission.MODIFY_PHONE_STATE)
            }, release = {
                try { automation!!.dropShellPermissionIdentity() }
                catch (error: Exception) { stage = "delegation-cleanup"; throw error }
            }) {
            val config = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, configBinder)
            val method = Class.forName("com.android.internal.telephony.ICarrierConfigLoader").getMethod("overrideConfig",
                Int::class.javaPrimitiveType, PersistableBundle::class.java, Boolean::class.javaPrimitiveType)
            if (targetContext.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw SecurityException("PHONE_PERMISSION_REQUIRED")
            val sims = targetContext.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            check(sims.count { plan.matches(it.simSlotIndex, it.subscriptionId, it.mccString, it.mncString, it.isEmbedded) } == 1) { "SELECTION_CHANGED" }
            val overrides=when {
                BundledProfiles.isApply(plan.configMode) -> LocalPbProfile.bundle(targetContext,requireNotNull(BundledProfiles.forMode(plan.configMode)).id)
                BundledProfiles.isClear(plan.configMode) || plan.reset -> null
                else -> PersistableBundle()
            }
            if(BundledProfiles.isApply(plan.configMode)) {
                stage="original-network-required"
                val numeric=targetContext.getSystemService(android.telephony.TelephonyManager::class.java).createForSubscriptionId(plan.subId).simOperator
                check(numeric==plan.sourceMcc+plan.sourceMnc) { "RESTORE_ORIGINAL_MCC_MNC_FIRST" }
            }
            if(BundledProfiles.isApply(plan.configMode)) {
                stage="apn-shizuku"
                entered=true // A lost binder reply cannot prove that no insert happened.
                val apn=gate.installApns(token)
                entered=apn.getBoolean("mutated")
                if(!apn.getBoolean("ok")) {
                    code=if(entered) "OUTCOME_UNCERTAIN" else "PRECHECK_FAILED"
                    detail=apn.getString("detail") ?: "native2:apn-shizuku:invalid-result"
                    return@run
                }
            }
            stage = "carrier-config"; gate.progress(token, stage); entered = true
            // Production firmware permits this app to replace only runtime overrides.
            val configStarted=android.os.SystemClock.elapsedRealtime()
            for(write in CarrierConfigWritePolicy.writes(plan)) {
                stage = when {
                    write.clear && write.persistent -> "carrier-config-clear-persistent"
                    write.persistent -> "carrier-config-save-persistent"
                    write.clear -> "carrier-config-clear-temporary"
                    else -> "carrier-config-apply-temporary"
                }
                method.invoke(config, plan.subId, if(write.clear) null else overrides, write.persistent)
            }
            val configMillis=android.os.SystemClock.elapsedRealtime()-configStarted
            stage = "carrier-identity"
            val identity = gate.identity(token)
            code = identity.getString("status") ?: "OUTCOME_UNCERTAIN"
            detail = identity.getString("detail") ?: "native2:identity:missing-result"
            if(BundledProfiles.isApply(plan.configMode) && code=="MODIFIER_REPORTED_SUCCESS") {
                detail+=":cfg=${configMillis}ms"
                detail+=if(plan.persistConfig) ":persistence=requested" else ":persistence=temporary"
                var verified=false
                for(attempt in 0..5) {
                    if(LocalPbProfile.readback(targetContext,plan.subId,requireNotNull(BundledProfiles.forMode(plan.configMode)).id)) { verified=true;break }
                    if(attempt<5) Thread.sleep(150)
                }
                detail+=if(verified) ":readback-verified" else ":readback-unconfirmed"
            }
            }
        } catch (error: Exception) {
            val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
            code = if (entered || stage == "delegation-cleanup") "OUTCOME_UNCERTAIN" else "PRECHECK_FAILED"
            detail = CarrierErrorDetails.encode(stage,cause)
            android.util.Log.e("SIMCarrierConfig",detail)
        } finally {
            finish(Activity.RESULT_OK, Bundle().apply { putString("status", code); putString("detail", detail) })
        }
    }
}

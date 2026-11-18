package com.example.simregionswitcher

/** Original subscription identity and requested profile travel together, never by slot alone. */
data class RegionPlan(
    val slot: Int, val subId: Int, val operator: String,
    val sourceMcc: String, val sourceMnc: String, val embedded: Boolean,
    val mcc: String, val mnc: String, val reset: Boolean = false, val configMode: String = "", val persistConfig: Boolean = false
) {
    val configOnly get()=configMode.isNotEmpty()
    fun valid() = slot in 0..1 && subId >= 0 && operator.length <= 100 &&
        sourceMcc.matches(Regex("[0-9]{3}")) && sourceMnc.matches(Regex("[0-9]{2,3}")) &&
        mcc.matches(Regex("[0-9]{3}")) && mnc.matches(Regex("[0-9]{2,3}")) &&
        (!reset || mcc == sourceMcc && mnc == sourceMnc) &&
        (!persistConfig || configMode == LocalPbRules.APPLY) &&
        LocalPbRules.validTarget(configMode,reset,sourceMcc,sourceMnc,mcc,mnc)
    fun matches(slot: Int, subId: Int, mcc: String?, mnc: String?, embedded: Boolean) =
        valid() && this.slot == slot && this.subId == subId && sourceMcc == mcc && sourceMnc == mnc && this.embedded == embedded
}
object RegionGuard {
    val completedApply = setOf("MODIFIER_REPORTED_SUCCESS", "MODIFIER_REPORTED_FAILURE", "INVALID_MODIFIER_REPLY", "TICKET_EXPIRED", "PRECHECK_FAILED", "CANCELLED", "MALFORMED_REPLY", "EMPTY_REPLY", "START_REJECTED", "OPEN_CARRIER_FIRST")
    fun canPrepare(locked: Boolean, blocked: Boolean, phase: String, code: String): Boolean =
        !blocked && (!locked || phase == "apply" && code in completedApply)
    fun canApply(prepared: RegionPlan?, displayed: RegionPlan?, hasTicket: Boolean, locked: Boolean, busy: Boolean) =
        prepared != null && prepared.valid() && displayed == prepared && hasTicket && !locked && !busy
}


/** Fixed typed payload. A restore never includes country overrides, nor an all-SIM sentinel. */
object RegionActionRules {
    fun modifierArguments(plan: RegionPlan): Map<String, Any> {
        require(plan.valid() && !plan.configOnly)
        return if (plan.reset) mapOf("select_sim_id" to plan.subId, "reset" to true)
        else mapOf("select_sim_id" to plan.subId, "country_mcc_override" to plan.mcc, "country_mnc_hint" to plan.mnc)
    }
}

/** One confirmed UUID may be observed repeatedly, but never sent twice. */
object DirectApplyRules {
    enum class Decision { OBSERVE, BLOCK, START }
    fun decide(sameRequest: Boolean, samePlan: Boolean, applyPhase: Boolean,
        unresolved: Boolean, attempted: Boolean, priorCode: String?): Decision = when {
        sameRequest -> if (samePlan && applyPhase) Decision.OBSERVE else Decision.BLOCK
        unresolved || attempted && priorCode !in RegionGuard.completedApply -> Decision.BLOCK
        else -> Decision.START
    }
}

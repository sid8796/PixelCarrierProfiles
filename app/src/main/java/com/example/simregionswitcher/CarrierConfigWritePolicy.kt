package com.example.simregionswitcher

/** The tested production Pixel firmware restricts persistence to system applications. */
object CarrierConfigWritePolicy {
    data class Write(val clear: Boolean, val persistent: Boolean)
    fun writes(plan: RegionPlan): List<Write> {
        require(plan.valid() && LocalPbRules.canSubmitMode(plan.configMode) && !plan.persistConfig) { "PERSISTENCE_REQUIRES_SYSTEM_APP" }
        return when {
            BundledProfiles.isApply(plan.configMode) -> listOf(Write(true, false), Write(false, false))
            BundledProfiles.isClear(plan.configMode) || plan.reset -> listOf(Write(true, false))
            else -> listOf(Write(false, false))
        }
    }
}

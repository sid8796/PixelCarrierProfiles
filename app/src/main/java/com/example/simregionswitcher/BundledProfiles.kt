package com.example.simregionswitcher

/** Only bundled profiles bound to the real SIM operator can be applied. */
object BundledProfiles {
    data class Profile(val id:String,val mcc:String,val mnc:String,val applyMode:String,val clearMode:String) { val experimental get()=mcc=="250" }
    val available=listOf(Profile("a1_by","257","01",LocalPbRules.APPLY,LocalPbRules.CLEAR),Profile("mts_by","257","02",MtsByRules.APPLY,MtsByRules.CLEAR),Profile("mts_ru","250","01",MtsRuRules.APPLY,MtsRuRules.CLEAR),Profile("beeline_ru","250","99",BeelineRuRules.APPLY,BeelineRuRules.CLEAR),Profile("megafon_ru","250","02",MegafonRuRules.APPLY,MegafonRuRules.CLEAR),Profile("t2_ru","250","20",T2RuRules.APPLY,T2RuRules.CLEAR))
    fun isApply(mode:String?)=available.any { it.applyMode==mode }
    fun isClear(mode:String?)=available.any { it.clearMode==mode }
    fun forMode(mode:String?)=available.singleOrNull { mode==it.applyMode || mode==it.clearMode }
    fun find(mcc:String?,mnc:String?)=available.singleOrNull { it.mcc==mcc && it.mnc==mnc }
    fun canApply(plan:RegionPlan?):Boolean {
        if(plan==null || !plan.valid() || plan.reset || plan.persistConfig) return false
        val profile=find(plan.sourceMcc,plan.sourceMnc) ?: return false
        return plan.mcc==profile.mcc && plan.mnc==profile.mnc && plan.configMode in setOf(profile.applyMode,profile.clearMode)
    }
}

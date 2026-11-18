package com.example.simregionswitcher

/** Never infer applied configuration from a bundled profile or operation history. */
object ProfileOverviewRules {
    fun durationSeconds(millis:Long?):Long? = millis?.takeIf { it in 0..86400000 }?.let { (it+500)/1000 }
    fun expectedFor(id:String):Map<String,Any> = when(id) {
        "a1_by" -> LocalPbRules.expectedFlags+LocalPbRules.expectedIntegers+LocalPbRules.expectedStrings
        "mts_by" -> MtsByRules.expectedFlags+MtsByRules.expectedIntegers+MtsByRules.expectedStrings
        "mts_ru" -> MtsRuRules.expectedFlags+MtsRuRules.expectedIntegers
        "beeline_ru" -> BeelineRuRules.expectedFlags+BeelineRuRules.expectedIntegers+BeelineRuRules.expectedStrings
        "t2_ru" -> T2RuRules.expectedFlags+T2RuRules.expectedIntegers+T2RuRules.expectedStrings
        "megafon_ru" -> MegafonRuRules.expectedFlags+MegafonRuRules.expectedIntegers+MegafonRuRules.expectedStrings+MegafonRuRules.expectedArrays
        else -> error("UNKNOWN_PROFILE")
    }
    fun differencesFor(values:Map<String,Any?>?,id:String):List<Difference> =
        if(values==null) emptyList() else expectedFor(id).mapNotNull { (key,want) ->
            if(values[key]==want) null else Difference(key,want,values[key])
        }
    fun checkStateFor(values:Map<String,Any?>?,id:String,error:String?=null):CheckState = when {
        error!=null -> CheckState.FAILED
        values==null -> CheckState.NOT_CHECKED
        differencesFor(values,id).isEmpty() -> CheckState.ACTIVE
        else -> CheckState.DIFFERENT
    }
    fun configVerified(values:Map<String,Any?>?,mts:Boolean=false,ru:Boolean=false,beeline:Boolean=false):Boolean = values!=null &&
        (if(beeline) BeelineRuRules.expectedFlags else if(ru) MtsRuRules.expectedFlags else if(mts) MtsByRules.expectedFlags else LocalPbRules.expectedFlags).all { (key,value) -> values[key] is Boolean && values[key]==value } &&
        (if(beeline) BeelineRuRules.expectedIntegers else if(ru) MtsRuRules.expectedIntegers else if(mts) MtsByRules.expectedIntegers else LocalPbRules.expectedIntegers).all { (key,value) -> values[key] is Int && values[key]==value } &&
        (if(beeline) BeelineRuRules.expectedStrings else if(ru) emptyMap() else if(mts) MtsByRules.expectedStrings else LocalPbRules.expectedStrings).all { (key,value) -> values[key] is String && values[key]==value }

    enum class CheckState { NOT_CHECKED, ACTIVE, DIFFERENT, FAILED }
    data class Difference(val key:String,val expected:Any,val actual:Any?)
    fun expected(mts:Boolean=false,ru:Boolean=false,beeline:Boolean=false):Map<String,Any> =
        (if(beeline) BeelineRuRules.expectedFlags else if(ru) MtsRuRules.expectedFlags else if(mts) MtsByRules.expectedFlags else LocalPbRules.expectedFlags)+
        (if(beeline) BeelineRuRules.expectedIntegers else if(ru) MtsRuRules.expectedIntegers else if(mts) MtsByRules.expectedIntegers else LocalPbRules.expectedIntegers)+
        (if(beeline) BeelineRuRules.expectedStrings else if(ru) emptyMap<String,String>() else if(mts) MtsByRules.expectedStrings else LocalPbRules.expectedStrings)
    fun differences(values:Map<String,Any?>?,mts:Boolean=false,ru:Boolean=false,beeline:Boolean=false):List<Difference> =
        if(values==null) emptyList() else expected(mts,ru,beeline).mapNotNull { (key,want) ->
            if(values[key]==want) null else Difference(key,want,values[key])
        }
    fun checkState(values:Map<String,Any?>?,mts:Boolean=false,ru:Boolean=false,error:String?=null,beeline:Boolean=false):CheckState = when {
        error!=null -> CheckState.FAILED
        values==null -> CheckState.NOT_CHECKED
        configVerified(values,mts,ru,beeline) -> CheckState.ACTIVE
        else -> CheckState.DIFFERENT
    }
}

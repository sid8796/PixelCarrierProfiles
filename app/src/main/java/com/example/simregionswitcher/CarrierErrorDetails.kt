package com.example.simregionswitcher

/** Bounded transport-safe exception reason. No stack, subscriber identity or arbitrary newlines. */
object CarrierErrorDetails {
    fun encode(stage:String,error:Throwable):String {
        val message=error.message.orEmpty()
            .replace(Regex("[\\r\\n\\t]+")," ")
            .replace(Regex("[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+"),"[redacted]")
            .replace(Regex("[+]?[0-9]{7,}"),"[redacted]")
            .take(500)
        val encoded=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(message.toByteArray(Charsets.UTF_8))
        return "native2:$stage:${error.javaClass.simpleName}:reason64=$encoded"
    }
}

/** Only explicit returned CarrierConfig permission refusals; never timeout or lost-reply outcomes. */
object CarrierRefusalRules {
    fun canAcknowledge(busy:Boolean,code:String,detail:String,plan:RegionPlan?):Boolean =
        !busy && BundledProfiles.isApply(plan?.configMode) && plan?.valid()==true && code=="OUTCOME_UNCERTAIN" &&
        detail.matches(Regex("native2:carrier-config(?:-clear-persistent|-save-persistent)?:SecurityException(?::reason64=[A-Za-z0-9_-]*)?"))
}

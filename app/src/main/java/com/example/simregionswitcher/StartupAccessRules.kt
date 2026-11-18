package com.example.simregionswitcher

/** Permission prompts only; this policy never applies a carrier profile. */
object StartupAccessRules {
    enum class Prompt { NONE, PHONE, SHIZUKU }
    fun next(visible:Boolean,pending:Boolean,phoneGranted:Boolean,phoneSeen:Boolean,access:String?,shizukuSeen:Boolean):Prompt = when {
        !visible || pending -> Prompt.NONE
        !phoneGranted && !phoneSeen -> Prompt.PHONE
        !phoneGranted -> Prompt.NONE
        access=="SHIZUKU_NOT_AUTHORIZED" && !shizukuSeen -> Prompt.SHIZUKU
        else -> Prompt.NONE
    }
}

package com.example.simregionswitcher

import java.util.concurrent.atomic.AtomicReference
/** Completion belongs to one immutable request; timeout does not discard it. */
class TrialCompletion<T>(val requestId: String, val phase: String) {
    private val result = AtomicReference<T?>()
    fun finish(value: T): Boolean = result.compareAndSet(null, value)
    fun peek(): T? = result.get()
}
object TrialRecoveryRules {
    fun afterReboot(phase: String, previousBoot: Int, currentBoot: Int): Boolean =
        phase == "prepare" && previousBoot >= 0 && currentBoot > previousBoot
    fun accepts(expectedId: String, expectedPhase: String, id: String?, phase: String?): Boolean =
        expectedId.isNotBlank() && expectedId == id && expectedPhase == phase && expectedPhase in setOf("prepare", "apply")
}

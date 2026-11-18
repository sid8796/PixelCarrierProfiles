package com.example.simregionswitcher

/** Startup only reads APN; it never changes selected APN or replaces an existing snapshot. */
object ApnAutoSaveRules {
    fun storageKey(slot: Int, subId: Int, cardId: Int, mcc: String, mnc: String) =
        "working_${cardId}_${subId}_${slot}_${mcc}_${mnc}"
    enum class Result { PRESERVED, SAVED, FAILED }
    fun shouldStart(visible: Boolean, ready: Boolean, busy: Boolean, hasSaved: Boolean, tried: Boolean) =
        visible && ready && !busy && !hasSaved && !tried
    fun <T> ensure(existing: () -> T?, read: () -> T, persist: (T) -> Boolean): Result {
        if (existing() != null) return Result.PRESERVED
        val selected = read()
        if (existing() != null) return Result.PRESERVED
        return if (persist(selected)) Result.SAVED else Result.FAILED
    }
    fun mayChangeProfile(reset: Boolean, snapshotReady: Boolean) = reset || snapshotReady
}

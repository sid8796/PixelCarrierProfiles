package com.example.simregionswitcher

/** A number read may only follow this refresh's successful capability reply in the visible SIM. */
object ImsRefreshRules {
    fun readNumber(expected: Long, completed: Long, sameVisibleTarget: Boolean, visible: Boolean,
        busy: Boolean, failed: Boolean) = expected > 0 && expected == completed && sameVisibleTarget && visible && !busy && !failed
}

package com.example.simregionswitcher

/** Stable snapshot identity: the selected UI slot never relabels a saved result. */
data class ReadResultKey(val slot: Int, val subId: Int, val operation: ReadOperation)
data class ReadEntry<T>(val key: ReadResultKey, val requestId: Long, val value: T)
data class ReadArchive<T>(val entries: List<ReadEntry<T>> = emptyList()) {
    fun put(key: ReadResultKey, requestId: Long, value: T): ReadArchive<T> {
        if (entries.any { it.key == key && it.requestId >= requestId }) return this
        return ReadArchive((entries.filterNot { it.key == key } + ReadEntry(key, requestId, value)).takeLast(8))
    }
}

object ReadLifecycle {
    fun accepts(activeId: Long?, expectedId: Long, deliveredId: Long): Boolean =
        activeId != null && activeId == expectedId && expectedId == deliveredId
}

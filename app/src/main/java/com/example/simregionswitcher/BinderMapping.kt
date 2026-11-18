package com.example.simregionswitcher

/** Result of a direct ISub slot query. Unknown is never treated as a SIM change. */
data class BinderMappingEvidence(val actualSubId: Int? = null, val error: String? = null)

object BinderMapping {
    fun assess(expectedSubId: Int, raw: Any?): BinderMappingEvidence {
        if (expectedSubId < 0) return BinderMappingEvidence(error = "INVALID_SELECTION")
        val id = when (raw) {
            is Int -> raw
            is IntArray -> when {
                raw.isEmpty() -> return BinderMappingEvidence(error = "REMOTE_MAPPING_UNAVAILABLE")
                raw.size != 1 -> return BinderMappingEvidence(error = "REMOTE_MAPPING_AMBIGUOUS")
                else -> raw.single()
            }
            null -> return BinderMappingEvidence(error = "REMOTE_MAPPING_UNAVAILABLE")
            else -> return BinderMappingEvidence(error = "REMOTE_MAPPING_INVALID_TYPE")
        }
        return when {
            id < 0 -> BinderMappingEvidence(error = "REMOTE_MAPPING_UNAVAILABLE")
            id != expectedSubId -> BinderMappingEvidence(id, "REMOTE_SELECTION_CHANGED")
            else -> BinderMappingEvidence(id)
        }
    }
}

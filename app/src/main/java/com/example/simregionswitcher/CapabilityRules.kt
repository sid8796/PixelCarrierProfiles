package com.example.simregionswitcher

data class CapabilityProbe(val key: String, val capability: Int, val technology: Int)
data class CapabilitySnapshot(val values: Map<String, Boolean?>, val errors: Map<String, String>)

object CapabilityRules {
    // MmTel capabilities VOICE=1, VIDEO=2, UT=4. Technologies LTE=0, IWLAN=1, CROSS_SIM=2, NR=3.
    val probes = listOf("voice" to 1, "video" to 2, "ut" to 4).flatMap { (name, capability) ->
        listOf("lte" to 0, "wifi" to 1, "cross_sim" to 2, "nr" to 3).map { (tech, code) ->
            CapabilityProbe("${name}_$tech", capability, code)
        }
    }
    val resultKeys = listOf("state_before", "state_after", "transport") +
        probes.flatMap { listOf(it.key, "${it.key}_error") }
    fun decode(raw: Map<*, *>): ParsedRead {
        val state = TransportRules.decode(raw["state_before"], raw["state_after"], raw["transport"])
        if (state.error != null) return state
        val values = probes.associate { probe -> probe.key to
            if (raw.containsKey("${probe.key}_error")) null else raw[probe.key] as? Boolean }
        val errors = probes.mapNotNull { probe ->
            if (values[probe.key] != null) null else probe.key to
                ((raw["${probe.key}_error"] as? String)?.take(100) ?: "MISSING_OR_INVALID_VALUE")
        }.toMap()
        if (state.transport?.state != 2 && values.values.any { it == true })
            return ParsedRead(error = "IMS_STATE_CHANGED")
        return state.copy(capabilities = CapabilitySnapshot(values, errors))
    }
    /** Available on at least one queried tech; Unknown if a missing tech could change false to true. */
    fun aggregate(values: Collection<Boolean?>): Boolean? = when {
        values.any { it == true } -> true
        values.isEmpty() || values.any { it == null } -> null
        else -> false
    }
    fun aggregate(snapshot: CapabilitySnapshot, name: String): Boolean? =
        aggregate(probes.filter { it.key.startsWith("${name}_") }.map { snapshot.values[it.key] })
}

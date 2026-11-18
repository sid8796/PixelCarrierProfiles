package com.example.simregionswitcher

/** Android slot/subscription mapping only; never an IMS command argument. */
data class SimRecord(val slot: Int, val subscriptionId: Int)
enum class MappingState { UNAVAILABLE, EMPTY_SLOT, AMBIGUOUS, OBSERVED }
data class SlotMapping(val state: MappingState, val subscriptionId: Int? = null)

object SlotResolver {
    fun resolve(slot: Int, records: List<SimRecord>?): SlotMapping {
        if (slot < 0 || records == null) return SlotMapping(MappingState.UNAVAILABLE)
        val matches = records.filter { it.slot == slot }
        return when {
            matches.isEmpty() -> SlotMapping(MappingState.EMPTY_SLOT)
            matches.size != 1 || matches.single().subscriptionId < 0 -> SlotMapping(MappingState.AMBIGUOUS)
            records.count { it.subscriptionId == matches.single().subscriptionId } != 1 -> SlotMapping(MappingState.AMBIGUOUS)
            else -> SlotMapping(MappingState.OBSERVED, matches.single().subscriptionId)
        }
    }
}

package com.example.simregionswitcher

enum class ReadOperation(val className: String) {
    IMS("io.github.vvb2060.ims.privileged.ImsStatusReader"),
    CONFIG("io.github.vvb2060.ims.privileged.ConfigReader"),
    CAPABILITIES(""),
    TRANSPORT("") // Direct platform read; never an instrumentation component.
}

data class ParsedRead(
    val registered: Boolean? = null,
    val flags: Map<String, Boolean?>? = null,
    val error: String? = null,
    val detail: String? = null,
    val transport: TransportSnapshot? = null,
    val capabilities: CapabilitySnapshot? = null
)

/** Pure protocol rules: no transport and no write operation can be requested here. */
object ReaderProtocol {
    const val BRIDGE_PROTOCOL = 6
    fun validBridgeProtocol(value: Any?) = value is Int && value == BRIDGE_PROTOCOL
    const val PACKAGE = "io.github.vvb2060.ims.mod"
    const val VERSION = "3.9.1.r107.4312b5a9"
    const val VERSION_CODE = 107L
    val keys: List<String> = listOf(
        "carrier_volte_available_bool",
        "carrier_wfc_ims_available_bool",
        "carrier_vt_available_bool",
        "carrier_supports_ss_over_ut_bool",
        "carrier_cross_sim_ims_available_bool"
    )

    fun supported(version: String?, code: Long) = version == VERSION && code == VERSION_CODE
    fun validSelection(slot: Int, subId: Int, records: List<SimRecord>?): Boolean {
        val resolved = SlotResolver.resolve(slot, records)
        return subId >= 0 && resolved.state == MappingState.OBSERVED && resolved.subscriptionId == subId
    }

    /** Preserve absence: synthesizing missing *_error keys falsely invalidates valid booleans. */
    fun projectResult(operation: ReadOperation, contains: (String) -> Boolean, get: (String) -> Any?): Map<String, Any?> {
        val selected = when (operation) {
            ReadOperation.CAPABILITIES -> CapabilityRules.resultKeys
            ReadOperation.TRANSPORT -> listOf("state_before", "state_after", "transport")
            else -> keys
        }
        return selected.filter(contains).associateWith(get)
    }
    fun decode(operation: ReadOperation, resultCode: Int, payload: Map<String, Any?>?): ParsedRead {
        if (resultCode != -1) return ParsedRead(error = "CANCELLED")
        if (payload == null) return ParsedRead(error = "EMPTY_REPLY")
        if (payload.containsKey("result_msg")) {
            return ParsedRead(error = "READER_ERROR", detail = (payload["result_msg"] as? String)?.take(250))
        }
        if (!payload.containsKey("result")) return ParsedRead(error = "MISSING_RESULT")
        return when (operation) {
            ReadOperation.CAPABILITIES -> {
                val raw = payload["result"] as? Map<*, *> ?: return ParsedRead(error = "INVALID_RESULT_TYPE")
                CapabilityRules.decode(raw)
            }
            ReadOperation.TRANSPORT -> {
                val raw = payload["result"] as? Map<*, *> ?: return ParsedRead(error = "INVALID_RESULT_TYPE")
                TransportRules.decode(raw["state_before"], raw["state_after"], raw["transport"])
            }
            ReadOperation.IMS -> {
                val value = payload["result"]
                if (value is Boolean) ParsedRead(registered = value)
                else ParsedRead(error = "INVALID_RESULT_TYPE")
            }
            ReadOperation.CONFIG -> {
                val values = payload["result"]
                if (values !is Map<*, *>) ParsedRead(error = "INVALID_RESULT_TYPE")
                else ParsedRead(flags = keys.associateWith { values[it] as? Boolean })
            }
        }
    }
}


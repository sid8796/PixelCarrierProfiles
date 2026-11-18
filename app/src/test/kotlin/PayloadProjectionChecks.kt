import com.example.simregionswitcher.*

fun payloadProjectionChecks(): Int {
    var count = 0
    fun verify(name: String, ok: Boolean) { check(ok) { name }; count++ }
    fun project(raw: Map<String, Any?>, operation: ReadOperation = ReadOperation.CAPABILITIES) =
        ReaderProtocol.projectResult(operation, raw::containsKey) { raw[it] }
    val raw = mutableMapOf<String, Any?>("state_before" to 2, "state_after" to 2, "transport" to 2)
    CapabilityRules.probes.forEach { raw[it.key] = false }
    raw["voice_wifi"] = true; raw["ut_wifi"] = true
    val sent = project(raw)
    verify("absent error keys remain absent", CapabilityRules.probes.none { sent.containsKey("${it.key}_error") })
    val result = ReaderProtocol.decode(ReadOperation.CAPABILITIES, -1, mapOf("result" to sent))
    verify("voice WiFi survives full projection and decode", result.capabilities?.values?.get("voice_wifi") == true)
    verify("known false survives full projection and decode", result.capabilities?.values?.get("voice_lte") == false)
    verify("valid payload has no synthetic errors", result.capabilities?.errors?.isEmpty() == true)
    val oldBug = CapabilityRules.resultKeys.associateWith { raw[it] }
    verify("regression reproduces old all-unknown failure", CapabilityRules.decode(oldBug).capabilities?.values?.values?.all { it == null } == true)
    val denied = project(raw + ("voice_lte_error" to "SecurityException"))
    verify("actual error key preserved", denied["voice_lte_error"] == "SecurityException")
    val partial = CapabilityRules.decode(denied).capabilities!!
    verify("actual error remains unknown", partial.values["voice_lte"] == null)
    verify("one error does not erase other valid probes", partial.values["voice_wifi"] == true && partial.values["voice_nr"] == false)
    verify("explicit null error still treated as malformed error", CapabilityRules.decode(project(raw + ("voice_wifi_error" to null))).capabilities?.values?.get("voice_wifi") == null)
    verify("unknown fields not forwarded", !project(raw + ("unreviewed" to true)).containsKey("unreviewed"))
    verify("missing boolean not synthesized", !project(raw - "video_lte").containsKey("video_lte"))
    verify("missing boolean remains unknown on decode", CapabilityRules.decode(project(raw - "video_lte")).capabilities?.values?.get("video_lte") == null)
    verify("transport projection preserves only real keys", project(mapOf("state_before" to 0), ReadOperation.TRANSPORT) == mapOf("state_before" to 0))
    verify("config false and missing values preserved", project(mapOf(ReaderProtocol.keys[0] to false), ReadOperation.CONFIG) == mapOf(ReaderProtocol.keys[0] to false))
    return count
}

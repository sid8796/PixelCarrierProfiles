import com.example.simregionswitcher.*

fun capabilityChecks(): Int {
    var count = 0
    fun verify(name: String, ok: Boolean) { check(ok) { name }; count++ }
    fun raw(state: Int = 2): MutableMap<String, Any?> = mutableMapOf<String, Any?>(
        "state_before" to state, "state_after" to state, "transport" to 2).apply {
        CapabilityRules.probes.forEach { put(it.key, false) }
    }
    verify("only twelve fixed probes", CapabilityRules.probes.size == 12 && CapabilityRules.probes.map { it.key }.toSet().size == 12)
    verify("correct capability tech allowlist", CapabilityRules.probes.map { it.capability }.toSet() == setOf(1, 2, 4) && CapabilityRules.probes.map { it.technology }.toSet() == setOf(0, 1, 2, 3))
    val wifi = raw().apply { put("voice_wifi", true); put("ut_wifi", true) }
    val wifiResult = CapabilityRules.decode(wifi)
    verify("WiFi voice distinct from LTE/NR/CrossSIM", wifiResult.capabilities?.values?.let {
        it["voice_wifi"] == true && it["voice_lte"] == false && it["voice_nr"] == false && it["voice_cross_sim"] == false } == true)
    verify("UT available on one technology", CapabilityRules.aggregate(wifiResult.capabilities!!, "ut") == true)
    verify("all false video unavailable", CapabilityRules.aggregate(wifiResult.capabilities!!, "video") == false)
    val denied = raw().apply { put("video_lte_error", "SecurityException") }
    verify("failed probe not converted to false", CapabilityRules.decode(denied).capabilities?.values?.get("video_lte") == null)
    verify("reason retained", CapabilityRules.decode(denied).capabilities?.errors?.get("video_lte") == "SecurityException")
    verify("false plus unknown video aggregate unknown", CapabilityRules.aggregate(CapabilityRules.decode(denied).capabilities!!, "video") == null)
    verify("true plus unknown video remains available on at least one", CapabilityRules.aggregate(CapabilityRules.decode(denied.apply { put("video_nr", true) }).capabilities!!, "video") == true)
    verify("true with explicit error remains unknown", CapabilityRules.decode(raw().apply { put("voice_lte", true); put("voice_lte_error", "failed") }).capabilities?.values?.get("voice_lte") == null)
    verify("missing probe unknown", CapabilityRules.decode(raw().apply { remove("voice_lte") }).capabilities?.values?.get("voice_lte") == null)
    verify("wrong type not coerced", CapabilityRules.decode(raw().apply { put("voice_lte", "false") }).capabilities?.values?.get("voice_lte") == null)
    verify("empty aggregate unknown", CapabilityRules.aggregate(emptyList()) == null)
    verify("state changed discards capability snapshot", CapabilityRules.decode(raw().apply { put("state_after", 0) }).let { it.error == "IMS_STATE_CHANGED" && it.capabilities == null })
    verify("unregistered but available is inconclusive", CapabilityRules.decode(raw(0).apply { put("voice_wifi", true) }).error == "IMS_STATE_CHANGED")
    verify("unregistered false values accepted as platform report", CapabilityRules.decode(raw(0)).capabilities?.values?.values?.all { it == false } == true)
    verify("unknown state never known false", CapabilityRules.decode(raw().apply { put("state_before", null) }).error == "INVALID_REGISTRATION_STATE")
    verify("malformed envelope rejected", ReaderProtocol.decode(ReadOperation.CAPABILITIES, -1, mapOf("result" to true)).error == "INVALID_RESULT_TYPE")
    verify("cancelled envelope cannot be success", ReaderProtocol.decode(ReadOperation.CAPABILITIES, 0, mapOf("result" to wifi)).error == "CANCELLED")
    verify("direct operation has no instrumentation class", ReadOperation.CAPABILITIES.className.isEmpty())
    val key = ReadResultKey(1, 2, ReadOperation.CAPABILITIES)
    val saved = StoredRead(key, 1000, 2000, wifiResult)
    verify("capability snapshot roundtrip", SnapshotCodec.decode(SnapshotCodec.encode(listOf(saved))).single() == saved)
    val unknown = saved.copy(result = CapabilityRules.decode(denied))
    verify("unknown probes and reasons roundtrip", SnapshotCodec.decode(SnapshotCodec.encode(listOf(unknown))).single() == unknown)
    fun schema(bytes: ByteArray, version: Int): ByteArray {
        val changed = bytes.copyOf(); java.nio.ByteBuffer.wrap(changed, 4, 4).putInt(version)
        val crc = java.util.zip.CRC32().apply { update(changed, 0, changed.size - 8) }.value
        java.nio.ByteBuffer.wrap(changed, changed.size - 8, 8).putLong(crc)
        return changed
    }
    val legacy = StoredRead(ReadResultKey(0, 3, ReadOperation.TRANSPORT), 1000, 2000, TransportRules.decode(0, 0, -1))
    verify("2.4 schema1 cache still loads", SnapshotCodec.decode(schema(SnapshotCodec.encode(listOf(legacy)), 1)).single() == legacy)
    verify("schema1 cannot contain new capability record", runCatching { SnapshotCodec.decode(schema(SnapshotCodec.encode(listOf(saved)), 1)) }.isFailure)
    val legacyKey = ReadResultKey(1, 2, ReadOperation.TRANSPORT)
    val archive = ReadArchive<ParsedRead>().put(legacyKey, 1, TransportRules.decode(2, 2, 2)).put(key, 2, wifiResult)
    verify("capabilities separate from transport snapshot", archive.entries.size == 2)
    return count
}

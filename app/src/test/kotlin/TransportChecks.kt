import com.example.simregionswitcher.*

fun transportChecks(): Int {
    var count = 0
    fun verify(name: String, condition: Boolean) { check(condition) { name }; count++ }
    fun decode(a: Any?, b: Any?, t: Any?) = TransportRules.decode(a, b, t)
    verify("registered WWAN is cellular, not LTE inferred", decode(2, 2, 1).transport?.access == ImsAccess.CELLULAR)
    verify("registered WLAN not WiFi-versus-CrossSIM inferred", decode(2, 2, 2).transport?.access == ImsAccess.WLAN)
    verify("invalid transport is unknown but preserves reported state", decode(2, 2, -1).transport == TransportSnapshot(2, ImsAccess.UNKNOWN, -1))
    verify("future transport not coerced", decode(2, 2, 9).transport?.access == ImsAccess.UNKNOWN)
    verify("null transport unknown", decode(2, 2, null).transport?.access == ImsAccess.UNKNOWN)
    verify("string transport not coerced", decode(2, 2, "2").transport?.access == ImsAccess.UNKNOWN)
    verify("boolean transport not coerced", decode(2, 2, true).transport?.access == ImsAccess.UNKNOWN)
    verify("unregistered WLAN never labelled active", decode(0, 0, 2).transport?.access == ImsAccess.NOT_APPLICABLE)
    verify("registering WWAN never labelled active", decode(1, 1, 1).transport?.access == ImsAccess.NOT_APPLICABLE)
    verify("registration lost during read is inconclusive", decode(2, 0, 2).let { it.error == "IMS_STATE_CHANGED" && it.transport == null })
    verify("registration gained during read is inconclusive", decode(0, 2, 1).error == "IMS_STATE_CHANGED")
    verify("registering transition inconclusive", decode(1, 2, 2).error == "IMS_STATE_CHANGED")
    verify("missing initial state invalid", decode(null, 2, 2).error == "INVALID_REGISTRATION_STATE")
    verify("missing final state invalid", decode(2, null, 2).error == "INVALID_REGISTRATION_STATE")
    verify("string state invalid", decode("2", 2, 1).error == "INVALID_REGISTRATION_STATE")
    verify("boolean state invalid", decode(true, 2, 1).error == "INVALID_REGISTRATION_STATE")
    verify("unknown state invalid", decode(9, 9, 1).error == "INVALID_REGISTRATION_STATE")
    verify("negative state invalid", decode(-1, -1, 2).error == "INVALID_REGISTRATION_STATE")
    val map = mapOf("state_before" to 2, "state_after" to 2, "transport" to 2)
    verify("transport envelope decoded", ReaderProtocol.decode(ReadOperation.TRANSPORT, -1, mapOf("result" to map)).transport?.access == ImsAccess.WLAN)
    verify("cancelled response not success", ReaderProtocol.decode(ReadOperation.TRANSPORT, 0, mapOf("result" to map)).error == "CANCELLED")
    verify("error wins over valid-looking data", ReaderProtocol.decode(ReadOperation.TRANSPORT, -1, mapOf("result" to map, "result_msg" to "failed")).transport == null)
    verify("malformed result not accepted", ReaderProtocol.decode(ReadOperation.TRANSPORT, -1, mapOf("result" to true)).error == "INVALID_RESULT_TYPE")
    verify("transport cannot select instrumentation", ReadOperation.TRANSPORT.className.isEmpty())
    val key = ReadResultKey(1, 2, ReadOperation.TRANSPORT)
    val imsKey = ReadResultKey(1, 2, ReadOperation.IMS)
    val results = ReadArchive<ParsedRead>().put(imsKey, 1, ParsedRead(registered = true)).put(key, 2, decode(2, 2, 2))
    verify("platform snapshot separate from old IMS snapshot", results.entries.size == 2 && results.entries.first { it.key == imsKey }.value.registered == true)
    return count
}

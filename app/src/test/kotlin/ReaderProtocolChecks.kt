import com.example.simregionswitcher.*

fun readerProtocolChecks(): Int {
    var count = 0
    fun verify(name: String, valid: Boolean) { check(valid) { name }; count++ }
    fun ims(code: Int = -1, values: Map<String, Any?>? = null) = ReaderProtocol.decode(ReadOperation.IMS, code, values)
    fun config(values: Map<String, Any?>?) = ReaderProtocol.decode(ReadOperation.CONFIG, -1, values)
    verify("true is registered", ims(values = mapOf("result" to true)).registered == true)
    verify("false is known unregistered", ims(values = mapOf("result" to false)).let { it.registered == false && it.error == null })
    verify("false with error is unknown", ims(values = mapOf("result" to false, "result_msg" to "not ready")).let { it.error != null && it.registered == null })
    verify("true with error is also unknown", ims(values = mapOf("result" to true, "result_msg" to "failure")).error != null)
    verify("empty message is an error", ims(values = mapOf("result" to false, "result_msg" to "")).error != null)
    verify("null message is an error", ims(values = mapOf("result" to false, "result_msg" to null)).error != null)
    verify("missing result is unknown", ims(values = emptyMap()).error == "MISSING_RESULT")
    verify("null payload is unknown", ims().error == "EMPTY_REPLY")
    verify("string false is not Boolean", ims(values = mapOf("result" to "false")).error == "INVALID_RESULT_TYPE")
    verify("integer zero is not Boolean", ims(values = mapOf("result" to 0)).error != null)
    verify("cancelled true is not success", ims(0, mapOf("result" to true)).error == "CANCELLED")
    verify("unexpected resultCode fails", ims(1, mapOf("result" to true)).error != null)
    verify("config missing nested result fails", config(emptyMap()).error == "MISSING_RESULT")
    verify("config wrong type fails", config(mapOf("result" to false)).error != null)
    val first = ReaderProtocol.keys[0]
    val second = ReaderProtocol.keys[1]
    val partial = config(mapOf("result" to mapOf(first to true, second to false))).flags!!
    verify("true config flag retained", partial[first] == true)
    verify("false config flag retained", partial[second] == false)
    verify("missing config flag unknown", partial[ReaderProtocol.keys[2]] == null)
    verify("string flag unknown", config(mapOf("result" to mapOf(first to "true"))).flags!![first] == null)
    verify("empty nested map does not imply false", config(mapOf("result" to emptyMap<String, Any?>())).flags!!.values.all { it == null })
    verify("exact source version allowed", ReaderProtocol.supported("3.9.1.r107.4312b5a9", 107))
    verify("future version blocked", !ReaderProtocol.supported("3.9.2", 108))
    verify("same version wrong code blocked", !ReaderProtocol.supported(ReaderProtocol.VERSION, 108))
    verify("missing version blocked", !ReaderProtocol.supported(null, 107))
    verify("known slot/subId accepted", ReaderProtocol.validSelection(0, 3, listOf(SimRecord(0, 3), SimRecord(1, 2))))
    verify("slot never used as subId", !ReaderProtocol.validSelection(0, 0, listOf(SimRecord(0, 3))))
    verify("all-SIM sentinel rejected", !ReaderProtocol.validSelection(0, -1, listOf(SimRecord(0, 3))))
    verify("hot swap rejected", !ReaderProtocol.validSelection(0, 3, listOf(SimRecord(0, 9))))
    verify("ambiguous selection rejected", !ReaderProtocol.validSelection(0, 3, listOf(SimRecord(0, 3), SimRecord(0, 2))))
    verify("permissionless mapping rejected", !ReaderProtocol.validSelection(0, 3, null))
    verify("no writer operation exists", ReadOperation.entries.filter { it.className.isNotEmpty() }.map { it.className }.toSet() == setOf(
        "io.github.vvb2060.ims.privileged.ImsStatusReader", "io.github.vvb2060.ims.privileged.ConfigReader"))
    verify("config key allowlist fixed", ReaderProtocol.keys.size == 5 && ReaderProtocol.keys.toSet().size == 5)
    return count
}

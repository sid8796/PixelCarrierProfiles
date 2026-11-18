import com.example.simregionswitcher.*

fun binderMappingChecks(): Int {
    var count = 0
    fun verify(name: String, valid: Boolean) { check(valid) { name }; count++ }
    verify("direct integer mapping", BinderMapping.assess(3, 3) == BinderMappingEvidence(3))
    verify("legacy single-element mapping", BinderMapping.assess(3, intArrayOf(3)) == BinderMappingEvidence(3))
    verify("real mismatch retains observed id", BinderMapping.assess(3, 2) == BinderMappingEvidence(2, "REMOTE_SELECTION_CHANGED"))
    verify("missing binder result is unknown", BinderMapping.assess(3, null).error == "REMOTE_MAPPING_UNAVAILABLE")
    verify("invalid result is not a change", BinderMapping.assess(3, -1).error == "REMOTE_MAPPING_UNAVAILABLE")
    verify("legacy invalid result unknown", BinderMapping.assess(3, intArrayOf(-1)).error == "REMOTE_MAPPING_UNAVAILABLE")
    verify("legacy empty unknown", BinderMapping.assess(3, intArrayOf()).error == "REMOTE_MAPPING_UNAVAILABLE")
    verify("ambiguous array rejected", BinderMapping.assess(3, intArrayOf(3, 2)).error == "REMOTE_MAPPING_AMBIGUOUS")
    verify("string not coerced", BinderMapping.assess(3, "3").error == "REMOTE_MAPPING_INVALID_TYPE")
    verify("sentinel input rejected", BinderMapping.assess(-1, -1).error == "INVALID_SELECTION")
    verify("old protocol rejected", !ReaderProtocol.validBridgeProtocol(null))
    verify("string protocol rejected", !ReaderProtocol.validBridgeProtocol("2"))
    verify("current protocol accepted", ReaderProtocol.validBridgeProtocol(6))
    verify("unknown protocol rejected", !ReaderProtocol.validBridgeProtocol(7))
    return count
}

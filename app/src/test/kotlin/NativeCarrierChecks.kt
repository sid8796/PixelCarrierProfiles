import com.example.simregionswitcher.NativeCarrierTransaction as Tx

fun nativeCarrierChecks(): Int {
    var n = 0
    for (reset in listOf(false, true)) {
        for (failAt in listOf(-1, 0, 1, 2, 3, 4)) {
            val calls = mutableListOf<String>()
            val port = object : Tx.Port {
                fun record(s: String) { calls += s; if (calls.size - 1 == failAt) error("simulated") }
                override fun preflight() = record("preflight")
                override fun mapping() = record("mapping")
                override fun overrideConfig(reset: Boolean) = record("config:$reset")
                override fun identity(reset: Boolean) = record("identity:$reset")
            }
            val result = Tx.execute(reset, port)
            val expected = listOf("preflight", "mapping", "config:$reset", "mapping", "identity:$reset")
            check(calls == if (failAt < 0) expected else expected.take(failAt + 1)); n++
            check(result.code == when (failAt) { -1 -> "MODIFIER_REPORTED_SUCCESS"; 0,1 -> "PRECHECK_FAILED"; else -> "OUTCOME_UNCERTAIN" }); n++
            check(calls.count { it.startsWith("config:") } <= 1 && calls.count { it.startsWith("identity:") } <= 1); n++
            if (failAt >= 0) { check(result.error == "IllegalStateException"); n++ }
        }
    }
    return n
}

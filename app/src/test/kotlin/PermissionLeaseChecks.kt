import com.example.simregionswitcher.TemporaryShellPermissionLease as Lease
fun permissionLeaseChecks(): Int {
    var n = 0
    for (fail in listOf("none", "acquire", "write", "release")) {
        val calls = mutableListOf<String>()
        val result = runCatching {
            Lease.run(acquire = { calls += "acquire"; if (fail == "acquire") error("acquire") },
                release = { calls += "release"; if (fail == "release") error("release") }) {
                calls += "write"; if (fail == "write") error("write"); 42
            }
        }
        check(calls == if (fail == "acquire") listOf("acquire") else listOf("acquire", "write", "release")); n++
        check(result.isSuccess == (fail == "none")); n++
        if (fail == "none") { check(result.getOrThrow() == 42); n++ }
        else { check(result.exceptionOrNull()?.message == fail); n++ }
    }
    return n
}

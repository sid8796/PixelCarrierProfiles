import com.example.simregionswitcher.*

fun readArchiveChecks(): Int {
    var count = 0
    fun verify(name: String, valid: Boolean) { check(valid) { name }; count++ }
    val a1 = ReadResultKey(0, 3, ReadOperation.IMS)
    val orange = ReadResultKey(1, 2, ReadOperation.IMS)
    val config = ReadResultKey(1, 2, ReadOperation.CONFIG)
    val initial = ReadArchive<ParsedRead>().put(a1, 1, ParsedRead(registered = false))
    val dual = initial.put(orange, 2, ParsedRead(registered = true))
    verify("A1 false preserved when Orange true arrives", dual.entries.first { it.key == a1 }.value.registered == false)
    verify("Orange true separate", dual.entries.first { it.key == orange }.value.registered == true)
    val both = dual.put(config, 3, ParsedRead(flags = mapOf("carrier_volte_available_bool" to true)))
    verify("config does not replace IMS", both.entries.size == 3 && both.entries.first { it.key == orange }.value.registered == true)
    val changed = both.put(orange, 4, ParsedRead(error = "READER_ERROR"))
    verify("new error replaces same subscription snapshot", changed.entries.size == 3 && changed.entries.first { it.key == orange }.value.error == "READER_ERROR")
    verify("older callback cannot replace new snapshot", changed.put(orange, 2, ParsedRead(registered = true)) == changed)
    verify("duplicate callback is idempotent", changed.put(orange, 4, ParsedRead(registered = true)) == changed)
    verify("prior archive immutable", dual.entries.size == 2 && dual.entries.first { it.key == orange }.value.registered == true)
    val swapped = changed.put(ReadResultKey(1, 9, ReadOperation.IMS), 5, ParsedRead(registered = false))
    verify("new subscription does not relabel old SIM", swapped.entries.any { it.key == orange } && swapped.entries.any { it.key.subId == 9 })
    var bounded = swapped
    for (id in 6L..20L) bounded = bounded.put(ReadResultKey(0, id.toInt(), ReadOperation.IMS), id, ParsedRead(registered = true))
    verify("history bounded at eight snapshots", bounded.entries.size == 8)
    verify("most recent snapshot retained", bounded.entries.last().requestId == 20L)
    verify("matching live reply accepted", ReadLifecycle.accepts(10, 10, 10))
    verify("duplicate finished reply rejected", !ReadLifecycle.accepts(null, 10, 10))
    verify("reply cannot finish next request", !ReadLifecycle.accepts(11, 10, 10))
    verify("incorrect remote id rejected", !ReadLifecycle.accepts(10, 10, 11))
    verify("old callback closure rejected", !ReadLifecycle.accepts(11, 10, 11))
    return count
}

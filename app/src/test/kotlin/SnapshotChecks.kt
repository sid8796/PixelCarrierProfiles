import com.example.simregionswitcher.*

fun snapshotChecks(): Int {
    var count = 0
    fun verify(name: String, condition: Boolean) { check(condition) { name }; count++ }
    fun fails(name: String, action: () -> Unit) = verify(name, runCatching(action).isFailure)
    fun saved(slot: Int, sub: Int, op: ReadOperation, result: ParsedRead, late: Boolean = false) =
        StoredRead(ReadResultKey(slot, sub, op), 1000, 2000, result, late)
    val a1 = saved(0, 3, ReadOperation.IMS, ParsedRead(registered = false))
    val orange = saved(1, 2, ReadOperation.TRANSPORT, TransportRules.decode(2, 2, 2))
    val flags = saved(0, 3, ReadOperation.CONFIG,
        ParsedRead(flags = ReaderProtocol.keys.mapIndexed { i, key -> key to when (i % 3) { 0 -> true; 1 -> false; else -> null } }.toMap()))
    val error = saved(1, 2, ReadOperation.IMS, ParsedRead(error = "READER_ERROR", detail = "Нет ответа"))
    val values = listOf(a1, orange, flags, error)
    val bytes = SnapshotCodec.encode(values)
    val loaded = SnapshotCodec.decode(bytes)
    verify("all results survive codec roundtrip", loaded == values)
    verify("known false survives without becoming unknown", loaded.first().result.registered == false)
    verify("true/false/unknown config retained", loaded[2].result.flags == flags.result.flags)
    verify("errors not loaded as false registration", loaded.last().result.let { it.error == "READER_ERROR" && it.registered == null })
    verify("transport saved separately from IMS", loaded[1].result.transport?.access == ImsAccess.WLAN)
    verify("original dates and subscription retained", loaded[1].startedAt == 1000L && loaded[1].finishedAt == 2000L && loaded[1].key.subId == 2)
    verify("late marker preserved", SnapshotCodec.decode(SnapshotCodec.encode(listOf(a1.copy(late = true)))).single().late)
    verify("empty cache valid", SnapshotCodec.decode(SnapshotCodec.encode(emptyList())).isEmpty())
    val damaged = bytes.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
    fails("corrupt payload rejected by checksum") { SnapshotCodec.decode(damaged) }
    fails("truncated file rejected") { SnapshotCodec.decode(bytes.copyOf(bytes.size - 4)) }
    fails("oversize file rejected") { SnapshotCodec.decode(ByteArray(SnapshotCodec.MAX_BYTES + 1)) }
    fails("unknown schema rejected") {
        val other = bytes.copyOf(); other[7] = 9
        val crc = java.util.zip.CRC32().apply { update(other, 0, other.size - 8) }.value
        java.nio.ByteBuffer.wrap(other, other.size - 8, 8).putLong(crc)
        SnapshotCodec.decode(other)
    }
    fails("duplicate identity rejected on load") { SnapshotCodec.decode(SnapshotCodec.encode(listOf(a1, a1))) }
    fails("negative subscription not persisted") { SnapshotCodec.encode(listOf(a1.copy(key = a1.key.copy(subId = -1)))) }
    fails("invalid slot not persisted") { SnapshotCodec.encode(listOf(a1.copy(key = a1.key.copy(slot = 2)))) }
    fails("missing timestamps rejected") { SnapshotCodec.encode(listOf(a1.copy(startedAt = 0))) }
    fails("missing known IMS boolean rejected") { SnapshotCodec.encode(listOf(a1.copy(result = ParsedRead()))) }
    fails("invalid transport state rejected") { SnapshotCodec.encode(listOf(orange.copy(result = ParsedRead(transport = TransportSnapshot(7, ImsAccess.WLAN, 2))))) }
    fails("too many snapshots rejected") { SnapshotCodec.encode(List(9) { a1 }) }
    val source = listOf(SimRecord(1, 2), SimRecord(0, 3))
    verify("batch ordered by slot not subscription ID", BothSimPlan.create(source) == listOf(SimRecord(0, 3), SimRecord(1, 2)))
    verify("single active slot checked once", BothSimPlan.create(listOf(SimRecord(1, 2))) == listOf(SimRecord(1, 2)))
    fails("batch blocked without current mapping") { BothSimPlan.create(null) }
    fails("batch blocked without active SIM") { BothSimPlan.create(emptyList()) }
    fails("ambiguous slot blocks entire batch") { BothSimPlan.create(listOf(SimRecord(0, 3), SimRecord(0, 4), SimRecord(1, 2))) }
    fails("duplicate subscription across slots rejected") { BothSimPlan.create(listOf(SimRecord(0, 3), SimRecord(1, 3))) }
    fails("negative ID rejected") { BothSimPlan.create(listOf(SimRecord(0, -1))) }
    val plan = BothSimPlan.create(source)
    verify("captured plan does not relabel SIM after hot swap", plan[1].subscriptionId == 2 &&
        !ReaderProtocol.validSelection(plan[1].slot, plan[1].subscriptionId, listOf(SimRecord(0, 3), SimRecord(1, 9))))
    return count
}

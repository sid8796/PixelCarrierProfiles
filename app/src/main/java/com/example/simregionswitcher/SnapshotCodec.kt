package com.example.simregionswitcher

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class StoredRead(val key: ReadResultKey, val startedAt: Long, val finishedAt: Long,
    val result: ParsedRead, val late: Boolean = false)

/** Bounded versioned cache. No request queue, permission or SIM mapping is restored. */
object SnapshotCodec {
    const val MAX_BYTES = 32768
    fun encode(values: List<StoredRead>): ByteArray {
        require(values.size <= 8)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(0x53525334); out.writeInt(2); out.writeInt(values.size)
            for (v in values) {
                validate(v)
                out.writeInt(v.key.slot); out.writeInt(v.key.subId); out.writeUTF(v.key.operation.name)
                out.writeLong(v.startedAt); out.writeLong(v.finishedAt); out.writeBoolean(v.late)
                out.writeBoolean(v.result.error != null)
                if (v.result.error != null) {
                    out.writeUTF(v.result.error.take(100)); out.writeUTF(v.result.detail.orEmpty().take(1000))
                } else when (v.key.operation) {
                    ReadOperation.CAPABILITIES -> {
                        val t = requireNotNull(v.result.transport)
                        out.writeInt(t.state); out.writeBoolean(t.rawTransport != null)
                        if (t.rawTransport != null) out.writeInt(t.rawTransport)
                        val caps = requireNotNull(v.result.capabilities)
                        for (probe in CapabilityRules.probes) {
                            out.writeByte(when (caps.values[probe.key]) { true -> 1; false -> 0; null -> -1 })
                            out.writeUTF(caps.errors[probe.key].orEmpty().take(100))
                        }
                    }
                    ReadOperation.IMS -> out.writeBoolean(v.result.registered!!)
                    ReadOperation.CONFIG -> ReaderProtocol.keys.forEach { key ->
                        out.writeByte(when (v.result.flags?.get(key)) { true -> 1; false -> 0; null -> -1 })
                    }
                    ReadOperation.TRANSPORT -> v.result.transport!!.let {
                        out.writeInt(it.state); out.writeBoolean(it.rawTransport != null)
                        if (it.rawTransport != null) out.writeInt(it.rawTransport)
                    }
                }
            }
        }
        val payload = buffer.toByteArray()
        val crc = java.util.zip.CRC32().apply { update(payload) }.value
        DataOutputStream(buffer).writeLong(crc)
        return buffer.toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): List<StoredRead> {
        require(bytes.size <= MAX_BYTES)
        require(bytes.size >= 20)
        val payload = bytes.copyOf(bytes.size - 8)
        val expectedCrc = DataInputStream(ByteArrayInputStream(bytes, bytes.size - 8, 8)).readLong()
        require(java.util.zip.CRC32().apply { update(payload) }.value == expectedCrc)
        val input = DataInputStream(ByteArrayInputStream(payload))
        require(input.readInt() == 0x53525334)
        val schema = input.readInt(); require(schema in 1..2)
        val count = input.readInt(); require(count in 0..8)
        val result = (0 until count).map {
            val key = ReadResultKey(input.readInt(), input.readInt(), ReadOperation.valueOf(input.readUTF()))
            val started = input.readLong(); val finished = input.readLong(); val late = input.readBoolean()
            val value = if (input.readBoolean()) {
                val error = input.readUTF(); val detail = input.readUTF()
                require(error.length in 1..100 && detail.length <= 1000)
                ParsedRead(error = error, detail = detail.ifEmpty { null })
            } else when (key.operation) {
                ReadOperation.CAPABILITIES -> {
                    require(schema >= 2)
                    val state = input.readInt(); val rawTransport = if (input.readBoolean()) input.readInt() else null
                    val raw = mutableMapOf<String, Any?>("state_before" to state, "state_after" to state, "transport" to rawTransport)
                    for (probe in CapabilityRules.probes) {
                        raw[probe.key] = when (input.readByte().toInt()) { -1 -> null; 0 -> false; 1 -> true; else -> error("Invalid boolean") }
                        val reason = input.readUTF(); require(reason.length <= 100)
                        if (reason.isNotEmpty()) raw["${probe.key}_error"] = reason
                    }
                    CapabilityRules.decode(raw)
                }
                ReadOperation.IMS -> ParsedRead(registered = input.readBoolean())
                ReadOperation.CONFIG -> ParsedRead(flags = ReaderProtocol.keys.associateWith {
                    when (input.readByte().toInt()) { -1 -> null; 0 -> false; 1 -> true; else -> error("Invalid boolean") }
                })
                ReadOperation.TRANSPORT -> {
                    val state = input.readInt(); val raw = if (input.readBoolean()) input.readInt() else null
                    TransportRules.decode(state, state, raw)
                }
            }
            StoredRead(key, started, finished, value, late).also { validate(it) }
        }
        require(input.available() == 0 && result.map { it.key }.toSet().size == result.size)
        return result
    }
    private fun validate(v: StoredRead) {
        require(v.key.slot in 0..1 && v.key.subId >= 0 && v.startedAt > 0 && v.finishedAt > 0)
        if (v.result.error != null) { require(v.result.error.isNotEmpty()); return }
        when (v.key.operation) {
            ReadOperation.CAPABILITIES -> {
                val t = requireNotNull(v.result.transport)
                val caps = requireNotNull(v.result.capabilities)
                val raw = mutableMapOf<String, Any?>("state_before" to t.state, "state_after" to t.state, "transport" to t.rawTransport)
                caps.values.forEach { (k, value) -> raw[k] = value }
                caps.errors.forEach { (k, value) -> raw["${k}_error"] = value }
                val checked = CapabilityRules.decode(raw)
                require(checked.error == null && checked.transport == t && checked.capabilities == caps)
            }
            ReadOperation.IMS -> require(v.result.registered != null)
            ReadOperation.CONFIG -> require(v.result.flags != null)
            ReadOperation.TRANSPORT -> {
                val t = requireNotNull(v.result.transport)
                require(TransportRules.decode(t.state, t.state, t.rawTransport).transport == t)
            }
        }
    }
}

object BothSimPlan {
    fun create(records: List<SimRecord>?): List<SimRecord> {
        require(records != null)
        val mappings = (0..1).map { it to SlotResolver.resolve(it, records) }
        require(mappings.none { it.second.state == MappingState.AMBIGUOUS })
        return mappings.filter { it.second.state == MappingState.OBSERVED }
            .map { SimRecord(it.first, requireNotNull(it.second.subscriptionId)) }.also { require(it.isNotEmpty()) }
    }
}

package com.example.simregionswitcher

/** Limited APN metadata only: never username/password, proxy or subscriber numbers. */
data class ApnRow(val id: Long, val name: String, val apn: String, val numeric: String,
    val type: String, val protocol: String, val roaming: String, val enabled: Boolean) {
    val types get() = type.lowercase(java.util.Locale.ROOT).split(',').map { it.trim() }.toSet()
    fun internet() = enabled && apn.isNotBlank() && apn.lowercase(java.util.Locale.ROOT) !in setOf("ims", "xcap", "sos", "emergency") && ("default" in types || "*" in types) &&
        types.none { it in setOf("ims", "xcap", "emergency", "ia") }
    fun sameProfile(other: ApnRow) = copy(id = other.id) == other
}
object ApnRules {
    fun endedAfterReboot(before: Int, now: Int) = before >= 0 && now > before
    fun uncertain(sent: Boolean, alreadyPending: Boolean, result: String) = (sent || alreadyPending) &&
        result !in setOf("APN_VERIFIED", "APN_NOT_SELECTED", "APN_PRECHECK_FAILED", "APN_TICKET_EXPIRED")
    val columns = listOf("_id", "name", "apn", "numeric", "type", "protocol", "roaming_protocol", "carrier_enabled")
    fun parse(output: String): List<ApnRow> {
        if (output.trim() == "No result found.") return emptyList()
        require(output.length <= 100_000) { "APN_REPLY_TOO_LARGE" }
        val rows = output.lineSequence().filter { it.isNotBlank() }.map { line ->
            require(line.matches(Regex("Row: [0-9]+ .*"))) { "APN_READ_FAILED" }
            val body = line.substringAfter(' ', "").substringAfter(' ')
            val tags = Regex("(?:^|, )(_id|name|apn|numeric|type|protocol|roaming_protocol|carrier_enabled)=").findAll(body).toList()
            require(tags.map { it.groupValues[1] } == columns) { "APN_REPLY_INVALID" }
            val v = tags.mapIndexed { i, m -> body.substring(m.range.last + 1, if (i + 1 < tags.size) tags[i + 1].range.first else body.length).let { if (it == "NULL" || it == "null") "" else it } }
            require(v.all { it.length <= 500 && it.none { c -> c.isISOControl() } }) { "APN_REPLY_INVALID" }
            require(v[7] in setOf("0", "1")) { "APN_REPLY_INVALID" }
            ApnRow(v[0].toLong().also { require(it >= 0) }, v[1], v[2], v[3], v[4], v[5], v[6], v[7] == "1")
        }.toList()
        require(rows.map { it.id }.distinct().size == rows.size) { "APN_REPLY_INVALID" }
        return rows
    }
    /** Match the saved metadata exactly, except database ID; no factory numeric/APN-name fallback. */
    fun candidates(saved: ApnRow, eligible: List<ApnRow>): List<ApnRow> =
        if (!saved.internet()) emptyList() else eligible.filter { it.internet() && saved.sameProfile(it) }
    fun candidate(saved: ApnRow, eligible: List<ApnRow>): ApnRow? {
        val matches = candidates(saved, eligible)
        matches.singleOrNull { it.id == saved.id }?.let { return it }
        return matches.singleOrNull()
    }
}

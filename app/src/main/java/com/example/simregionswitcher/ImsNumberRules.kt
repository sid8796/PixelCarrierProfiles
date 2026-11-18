package com.example.simregionswitcher

/** UI-only data, never part of ParsedRead, ReadArchive, SnapshotCodec or diagnostic report. */
data class ImsNumberSnapshot(val status: String, val numbers: List<String> = emptyList()) {
    override fun toString() = "ImsNumberSnapshot(status=$status,count=${numbers.size})"
}

object ImsNumberRules {
    val statuses = setOf("FOUND", "NO_NUMBER", "NO_URIS", "NOT_REGISTERED", "TIMEOUT", "STATE_CHANGED",
        "MAPPING_CHANGED", "ACCESS_DENIED", "API_UNAVAILABLE", "READ_ERROR", "CLEANUP_FAILED", "INVALID_REPLY")
    fun normalize(number: String): String? {
        if (number.length > 40 || number.any { it !in "+0123456789-.()" }) return null
        val canonical = number.filter { it !in "-.()" }
        return canonical.takeIf { Regex("\\+[1-9][0-9]{6,14}").matches(it) }
    }
    fun fromUris(uris: List<String>): List<String> = uris.take(32).mapNotNull { uri ->
        if (uri.length > 512 || uri.any { it.code <= 32 }) return@mapNotNull null
        val scheme = uri.substringBefore(':', "").lowercase()
        val body = uri.substringAfter(':', "")
        val user = when (scheme) {
            "tel" -> body.substringBefore(';')
            "sip", "sips" -> {
                if (body.count { it == '@' } != 1 || body.substringAfter('@').isEmpty()) return@mapNotNull null
                body.substringBefore('@').substringBefore(';')
            }
            else -> return@mapNotNull null
        }
        // Decode escapes without converting a literal '+' into an URL form space.
        val decoded = runCatching { java.net.URLDecoder.decode(user.replace("+", "%2B"), "UTF-8") }.getOrNull()
        decoded?.let { normalize(it) }
    }.distinct().take(8)

    fun decide(afterRegistered: Boolean?, changed: Boolean, received: Boolean, malformed: Boolean,
        uriSeen: Boolean, numbers: List<String>): ImsNumberSnapshot = when {
        changed || afterRegistered == false -> ImsNumberSnapshot("STATE_CHANGED")
        afterRegistered == null -> ImsNumberSnapshot("READ_ERROR")
        !received -> ImsNumberSnapshot("TIMEOUT")
        malformed -> ImsNumberSnapshot("INVALID_REPLY")
        !uriSeen -> ImsNumberSnapshot("NO_URIS")
        numbers.isEmpty() -> ImsNumberSnapshot("NO_NUMBER")
        else -> decode("FOUND", numbers)
    }
    fun decode(status: Any?, values: Any?): ImsNumberSnapshot {
        if (status !is String || status !in statuses) return ImsNumberSnapshot("INVALID_REPLY")
        if (status != "FOUND") return ImsNumberSnapshot(status)
        if (values !is List<*> || values.size !in 1..8 || values.any { it !is String || normalize(it) != it })
            return ImsNumberSnapshot("INVALID_REPLY")
        return ImsNumberSnapshot("FOUND", values.filterIsInstance<String>().distinct())
    }
}

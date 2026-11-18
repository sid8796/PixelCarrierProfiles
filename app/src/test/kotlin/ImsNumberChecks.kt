import com.example.simregionswitcher.*

fun imsNumberChecks(): Int {
    var count = 0
    fun verify(name: String, ok: Boolean) { check(ok) { name }; count++ }
    val phone = "+375291234567"
    fun parse(value: String) = ImsNumberRules.fromUris(listOf(value))
    verify("global tel URI", parse("tel:$phone") == listOf(phone))
    verify("SIP global number", parse("sip:$phone@ims.example;user=phone") == listOf(phone))
    verify("SIPS global number", parse("sips:$phone@ims.example") == listOf(phone))
    verify("escaped plus decoded", parse("sip:%2B375291234567@ims.example") == listOf(phone))
    verify("visual separators normalized", parse("tel:+375(29)123-45-67") == listOf(phone))
    verify("duplicate aliases do not duplicate number", ImsNumberRules.fromUris(listOf("tel:$phone", "sip:$phone@ims.example")) == listOf(phone))
    verify("IMSI-like username not phone", parse("sip:257011234567890@ims.example").isEmpty())
    verify("local tel not guessed from MCC", parse("tel:0291234567;phone-context=+375").isEmpty())
    verify("opaque SIP username not number", parse("sip:alice@ims.example").isEmpty())
    verify("global country cannot start zero", parse("tel:+0123456789").isEmpty())
    verify("more than fifteen digits rejected", parse("tel:+1234567890123456").isEmpty())
    verify("short identifiers rejected", parse("tel:+123").isEmpty())
    verify("Unicode digits rejected", parse("tel:+３７５２９１２３４５６７").isEmpty())
    verify("invalid escaping rejected", parse("sip:%QQ375291234567@ims.example").isEmpty())
    verify("control characters rejected", parse("tel:$phone\n").isEmpty())
    verify("SIP password not accepted", parse("sip:$phone:secret@ims.example").isEmpty())
    verify("HTTP URLs ignored", parse("https://example/$phone").isEmpty())
    verify("SIP missing host ignored", parse("sip:$phone@").isEmpty())
    verify("multiple numbers retained without choosing primary", ImsNumberRules.fromUris(listOf("tel:$phone", "tel:+48123456789")) == listOf(phone, "+48123456789"))
    verify("bounded number count", ImsNumberRules.fromUris((10..30).map { "tel:+3752912345$it" }).size == 8)
    verify("unknown reply status rejected", ImsNumberRules.decode("unreviewed", listOf(phone)).status == "INVALID_REPLY")
    verify("wrong list element rejected", ImsNumberRules.decode("FOUND", listOf(123)).status == "INVALID_REPLY")
    verify("wrong payload type rejected", ImsNumberRules.decode("FOUND", phone).status == "INVALID_REPLY")
    verify("empty FOUND rejected", ImsNumberRules.decode("FOUND", emptyList<String>()).status == "INVALID_REPLY")
    verify("noncanonical returned number rejected", ImsNumberRules.decode("FOUND", listOf("375291234567")).status == "INVALID_REPLY")
    verify("non-success drops any injected number", ImsNumberRules.decode("NO_NUMBER", listOf(phone)).numbers.isEmpty())
    verify("number redacted from model toString", !ImsNumberRules.decode("FOUND", listOf(phone)).toString().contains(phone))
    fun decision(after: Boolean? = true, changed: Boolean = false, received: Boolean = true,
        malformed: Boolean = false, uris: Boolean = true, numbers: List<String> = listOf(phone)) =
        ImsNumberRules.decide(after, changed, received, malformed, uris, numbers)
    verify("number accepted after stable registration", decision().numbers == listOf(phone))
    verify("deregistration suppresses number", decision(after = false).let { it.status == "STATE_CHANGED" && it.numbers.isEmpty() })
    verify("registering callback suppresses number", decision(changed = true).numbers.isEmpty())
    verify("unknown final state suppresses number", decision(after = null).let { it.status == "READ_ERROR" && it.numbers.isEmpty() })
    verify("timeout suppresses cached number", decision(received = false).let { it.status == "TIMEOUT" && it.numbers.isEmpty() })
    verify("malformed callback suppresses number", decision(malformed = true).let { it.status == "INVALID_REPLY" && it.numbers.isEmpty() })
    verify("no URI callback cannot expose number", decision(uris = false).let { it.status == "NO_URIS" && it.numbers.isEmpty() })
    verify("no parseable number explicit status", decision(numbers = emptyList()).status == "NO_NUMBER")
    verify("raw URI cannot escape decoder as number", decision(numbers = listOf("sip:$phone@ims.example")).status == "INVALID_REPLY")
    return count
}

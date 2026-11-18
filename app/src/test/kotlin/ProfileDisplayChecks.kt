import com.example.simregionswitcher.ProfileDisplayRules as Rules
fun profileDisplayChecks(): Int {
    var n=0
    fun expect(ok: Boolean) { check(ok);n++ }
    expect(Rules.inputReason("","")=="MCC_EMPTY")
    expect(Rules.inputReason("22","88")=="MCC_FORMAT")
    expect(Rules.inputReason("222","")=="MNC_EMPTY")
    expect(Rules.inputReason("222","8")=="MNC_FORMAT")
    expect(Rules.inputReason("257","01")==null)
    expect(Rules.inputReason("310","001")==null)
    expect(Rules.inputReason("222","٠١")=="MNC_FORMAT")
    expect(Rules.inputReason("２２２","88")=="MCC_FORMAT")
    expect(!Rules.apnVerified(null))
    expect(!Rules.apnVerified("APN_PENDING"))
    expect(!Rules.apnVerified("APN_NOT_SELECTED"))
    expect(Rules.apnVerified("APN_ALREADY_SELECTED"))
    expect(Rules.apnVerified("APN_VERIFIED"))
    return n
}

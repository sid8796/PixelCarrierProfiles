import com.example.simregionswitcher.A1TrialRules
fun a1TrialChecks(): Int {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    expect(A1TrialRules.matches(0, 3, "257", "01", false))
    expect(!A1TrialRules.matches(1, 2, "260", "03", true))
    expect(!A1TrialRules.matches(0, 2, "257", "01", false))
    expect(!A1TrialRules.matches(1, 3, "257", "01", false))
    expect(!A1TrialRules.matches(0, -1, "257", "01", false))
    expect(!A1TrialRules.matches(0, 3, "222", "88", false))
    expect(!A1TrialRules.matches(0, 3, "257", "1", false))
    expect(!A1TrialRules.matches(0, 3, "257", "01", true))
    expect(!A1TrialRules.matches(0, 3, null, "01", false))
    expect(A1TrialRules.allowedComponent(A1TrialRules.MODIFIER))
    expect(A1TrialRules.allowedComponent(A1TrialRules.CONFIG))
    expect(!A1TrialRules.allowedComponent("io.github.vvb2060.ims.privileged.ImsResetter"))
    expect(!A1TrialRules.allowedComponent(""))
    expect(A1TrialRules.ticketValid("issued", "issued", 100, 100))
    expect(A1TrialRules.ticketValid("issued", "issued", 100, 300100))
    expect(!A1TrialRules.ticketValid("issued", "issued", 100, 300101))
    expect(!A1TrialRules.ticketValid("issued", "issued", 100, 99))
    expect(!A1TrialRules.ticketValid(null, "issued", 100, 101))
    expect(!A1TrialRules.ticketValid("issued", "other", 100, 101))
    return count
}

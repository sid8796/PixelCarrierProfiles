import com.example.simregionswitcher.DirectApplyRules
fun directApplyChecks(): Int {
    var n=0
    fun expect(actual: DirectApplyRules.Decision, expected: DirectApplyRules.Decision) { check(actual==expected); n++ }
    val start=DirectApplyRules.Decision.START; val block=DirectApplyRules.Decision.BLOCK; val observe=DirectApplyRules.Decision.OBSERVE
    // First confirmation, replay after uncertain IPC, completed-response replay, target/mode mismatch.
    expect(DirectApplyRules.decide(false,false,false,false,false,null),start)
    expect(DirectApplyRules.decide(true,true,true,true,true,null),observe)
    expect(DirectApplyRules.decide(true,true,true,false,true,"MODIFIER_REPORTED_SUCCESS"),observe)
    expect(DirectApplyRules.decide(true,false,true,false,true,"MODIFIER_REPORTED_SUCCESS"),block)
    expect(DirectApplyRules.decide(true,true,false,false,false,"PREPARED"),block)
    // Another confirmation cannot bypass a still-running or unknown earlier request.
    expect(DirectApplyRules.decide(false,true,true,true,true,"MODIFIER_REPORTED_SUCCESS"),block)
    for(code in listOf(null,"PENDING_UNKNOWN","OUTCOME_UNCERTAIN","INVALID_REPLY","LOCKED"))
        expect(DirectApplyRules.decide(false,true,true,false,true,code),block)
    // A fresh manual request is permitted only after a known completion.
    for(code in listOf("MODIFIER_REPORTED_SUCCESS","MODIFIER_REPORTED_FAILURE","PRECHECK_FAILED","OPEN_CARRIER_FIRST","START_REJECTED"))
        expect(DirectApplyRules.decide(false,false,true,false,true,code),start)
    return n
}

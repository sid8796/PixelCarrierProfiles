import com.example.simregionswitcher.*

fun persistentConfigChecks():Int {
    var n=0
    fun expect(v:Boolean) { check(v);n++ }
    val a=RegionPlan(0,3,"A1","257","01",false,"257","01",configMode=LocalPbRules.APPLY)
    val other=a.copy(slot=1,subId=8)
    val state=mutableMapOf<Int,String>()
    fun run(p:RegionPlan) { for(w in CarrierConfigWritePolicy.writes(p)) { expect(!w.persistent);if(w.clear) state.remove(p.subId) else state[p.subId]="profile" } }
    run(other);run(a);expect(state.size==2)
    run(a.copy(configMode=LocalPbRules.CLEAR));expect(state[3]==null && state[8]=="profile")
    run(a);run(a.copy(configMode="",reset=true));expect(state[3]==null && state[8]=="profile")
    expect(runCatching { CarrierConfigWritePolicy.writes(a.copy(persistConfig=true)) }.exceptionOrNull()?.message=="PERSISTENCE_REQUIRES_SYSTEM_APP")
    LocalCarrierAuthorization.arm("temp",a)
    expect(!LocalCarrierAuthorization.consume("temp",a.copy(persistConfig=true)))
    expect(LocalCarrierAuthorization.consume("temp",a))
    val diagnostic=CarrierErrorDetails.encode("carrier-config-clear-temporary",SecurityException("Permission denied for uid 10378\nsubscriber 123456789012345 user@example.org"))
    expect(diagnostic.matches(Regex("[A-Za-z0-9_:=-]+")))
    val decoded=String(java.util.Base64.getUrlDecoder().decode(diagnostic.substringAfter("reason64=")),Charsets.UTF_8)
    expect("10378" in decoded && "123456789012345" !in decoded && "user@example.org" !in decoded && "\n" !in decoded)
    expect(CarrierErrorDetails.encode("carrier-config",SecurityException("a".repeat(3000))).length<1200)
    expect(CarrierRefusalRules.canAcknowledge(false,"OUTCOME_UNCERTAIN","native2:carrier-config:SecurityException",a))
    expect(CarrierRefusalRules.canAcknowledge(false,"OUTCOME_UNCERTAIN","native2:carrier-config-clear-persistent:SecurityException:reason64=abc",a))
    expect(!CarrierRefusalRules.canAcknowledge(true,"OUTCOME_UNCERTAIN","native2:carrier-config:SecurityException",a))
    expect(!CarrierRefusalRules.canAcknowledge(false,"PENDING_UNKNOWN","native2:carrier-config:SecurityException",a))
    expect(!CarrierRefusalRules.canAcknowledge(false,"OUTCOME_UNCERTAIN","native2:carrier-config:TimeoutException",a))
    expect(!CarrierRefusalRules.canAcknowledge(false,"OUTCOME_UNCERTAIN","native2:internet-selection:SecurityException",a))
    expect(!CarrierRefusalRules.canAcknowledge(false,"OUTCOME_UNCERTAIN","native2:carrier-config:SecurityException",a.copy(configMode="")))
    return n
}

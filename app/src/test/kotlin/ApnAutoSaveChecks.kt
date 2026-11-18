import com.example.simregionswitcher.ApnAutoSaveRules as Rules

fun apnAutoSaveChecks(): Int {
    val follow=com.example.simregionswitcher.ProfileApnFollowUp<String>()
    val plan=com.example.simregionswitcher.RegionPlan(0,3,"A1","257","01",false,"222","88")
    check(follow.take("one",plan)==null)
    follow.arm("one",plan,"saved")
    check(follow.take("other",plan)==null)
    check(follow.take("one",plan.copy(subId=9))==null)
    check(follow.take("one",plan)=="saved")
    check(follow.take("one",plan)==null)
    follow.arm("two",plan,"saved"); follow.clear()
    check(follow.take("two",plan)==null)
    check(runCatching { follow.arm("reset",plan.copy(reset=true),"saved") }.isFailure)
    var n=7
    fun expect(ok: Boolean) { check(ok) { "APN auto-save case ${n+1}" }; n++ }
    data class Snapshot(val label: String, val time: Long, val manual: Boolean)
    val manual=Snapshot("chosen by user",123,true)
    var saved: Snapshot?=manual; var reads=0; var writes=0
    val result=Rules.ensure(existing={saved}, read={reads++; Snapshot("new current",456,false)}, persist={writes++; saved=it; true})
    expect(result==Rules.Result.PRESERVED); expect(saved==manual); expect(reads==0 && writes==0)
    saved=null
    expect(Rules.ensure(existing={saved},read={reads++; Snapshot("selected",789,false)},persist={writes++;saved=it;true})==Rules.Result.SAVED)
    expect(saved==Snapshot("selected",789,false)); expect(reads==1 && writes==1)
    val captured=saved
    expect(Rules.ensure(existing={saved},read={reads++; Snapshot("changed after MCC",999,false)},persist={writes++;saved=it;true})==Rules.Result.PRESERVED)
    expect(saved==captured && reads==1 && writes==1)
    // A manual snapshot appearing during the read wins over the automatic candidate.
    saved=null; writes=0
    expect(Rules.ensure(existing={saved},read={saved=manual;Snapshot("auto",999,false)},persist={writes++;saved=it;true})==Rules.Result.PRESERVED)
    expect(saved==manual && writes==0)
    saved=null
    expect(runCatching { Rules.ensure(existing={saved},read={error("read failed")},persist={_: Snapshot -> writes++;true}) }.isFailure)
    expect(saved==null && writes==0)
    expect(Rules.ensure(existing={saved},read={manual},persist={false})==Rules.Result.FAILED)
    expect(saved==null)
    expect(Rules.shouldStart(true,true,false,false,false))
    expect(!Rules.shouldStart(false,true,false,false,false))
    expect(!Rules.shouldStart(true,false,false,false,false))
    expect(!Rules.shouldStart(true,true,true,false,false))
    expect(!Rules.shouldStart(true,true,false,true,false))
    expect(!Rules.shouldStart(true,true,false,false,true))
    expect(Rules.mayChangeProfile(false,true))
    expect(!Rules.mayChangeProfile(false,false))
    expect(Rules.mayChangeProfile(true,false))
    expect(Rules.mayChangeProfile(true,true))
    val key=Rules.storageKey(0,3,5,"257","01")
    for(other in listOf(Rules.storageKey(1,3,5,"257","01"), Rules.storageKey(0,2,5,"257","01"),
        Rules.storageKey(0,3,6,"257","01"), Rules.storageKey(0,3,5,"260","01"),Rules.storageKey(0,3,5,"257","001"))) expect(other!=key)
    val second=Rules.storageKey(1,2,6,"260","03")
    val store=mutableMapOf(key to manual, second to Snapshot("Orange",55,true))
    val prior=store.toMap()
    for(k in listOf(key,second)) expect(Rules.ensure(existing={store[k]},read={error("must not read existing SIM")},persist={store[k]=it;true})==Rules.Result.PRESERVED)
    expect(store==prior)
    return n
}

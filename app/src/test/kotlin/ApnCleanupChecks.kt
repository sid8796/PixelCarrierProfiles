import com.example.simregionswitcher.*

fun apnCleanupChecks():Int {
    var count=0
    fun expect(v:Boolean) { check(v) { "APN cleanup check ${count+1}" };count++ }
    fun rejects(f:()->Unit) { expect(runCatching(f).isFailure) }
    fun row(d:LocalPbApnRules.Definition,id:Int):Map<String,String> = LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"apn" to d.apn,"numeric" to "25701","sub_id" to "3","type" to d.type,
        "authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to "IPV4V6","roaming_protocol" to "IPV4V6",
        "bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"carrier_enabled" to "1",
        "user_editable" to d.editable.toString(),"name" to "A1 BY ${d.apn}","mvno_type" to "","mvno_match_data" to "",
        "apn_set_id" to "0","network_type_bitmask" to "0","carrier_id" to "568","edited" to "1","owned_by" to "1")
    val base=LocalPbApnRules.definitions.mapIndexed { i,d -> row(d,i+10) }
    val internet=base[0];val mms=base[1];val ims=base[2];val xcap=base[3]
    val legacy=internet+mapOf("_id" to "20","apn" to "web.velcom.by","user_editable" to "1")
    val legacyMms=mms+mapOf("_id" to "21","apn" to "mms.velcom.by","user_editable" to "1")
    val duplicate=internet+mapOf("_id" to "30","name" to "old duplicate")
    val duplicateMms=mms+mapOf("_id" to "31")
    fun ids(rows:List<Map<String,String>>,preferred:Long=10)=LocalPbApnCleanup.candidates(rows,3,preferred).map { it.getValue("_id").toInt() }
    expect(ids(base).isEmpty())
    expect(ids(base+legacy+legacyMms+duplicate+duplicateMms)==listOf(20,21,30,31))
    expect(ids(base+duplicate,30)==listOf(10)) // Keep the currently selected exact Internet row.
    val serviceDuplicates=listOf(ims+mapOf("_id" to "40"),xcap+mapOf("_id" to "41"))
    expect(ids(base+serviceDuplicates).isEmpty())
    for(extra in listOf(
        legacy+mapOf("sub_id" to "4"),legacy+mapOf("sub_id" to "-1"),legacy+mapOf("type" to "ims"),
        legacy+mapOf("type" to "default,emergency"),legacy+mapOf("type" to "*"),legacy+mapOf("type" to ""),
        legacy+mapOf("user_visible" to "0"),legacy+mapOf("mvno_type" to "imsi"),legacy+mapOf("owned_by" to "0"),
        legacy+mapOf("carrier_id" to "1659"),legacy+mapOf("carrier_id" to "0"),legacy+mapOf("apn" to "custom.example"),
        legacyMms+mapOf("type" to "mms,ims"),duplicate+mapOf("network_type_bitmask" to "4096"),
        duplicate+mapOf("apn_set_id" to "1"),duplicate+mapOf("sub_id" to "4"),
        duplicate+mapOf("infrastructure_bitmask" to "2"),duplicate+mapOf("esim_bootstrap_provisioning" to "1"),
        duplicate+mapOf("profile_id" to "7"),duplicate+mapOf("mtu_v4" to "1400"))) {
        expect(ids(base+extra).isEmpty())
    }
    rejects { ids(base+listOf(legacy+mapOf("numeric" to "26003"))) }
    rejects { LocalPbApnCleanup.candidates(base, -1,10) }
    rejects { ids(base,20) }
    rejects { ids(base.filter { it!==ims }+legacy) }
    rejects { ids(base+listOf(internet+mapOf("_id" to "50","protocol" to "IP"))) }
    val old=base.take(2).map { it+mapOf("user_editable" to "1") }
    expect(LocalPbApnRules.readOnlyUpdates(old,3)==listOf(10L,11L))
    expect(LocalPbApnRules.readOnlyUpdates(base,3).isEmpty())
    expect(LocalPbApnRules.readOnlyUpdates(old,4).isEmpty())
    expect(LocalPbApnRules.readOnlyUpdates(listOf(ims,xcap),3).isEmpty())
    for(change in listOf(mapOf("user_editable" to "2"),mapOf("proxy" to "proxy.example"),mapOf("protocol" to "IP"),mapOf("type" to "default,ims"),mapOf("owned_by" to "0"),mapOf("mvno_type" to "imsi"))) {
        rejects { LocalPbApnRules.readOnlyUpdates(listOf(old[0]+change),3) }
    }
    val predicate=LocalPbApnRules.rowSelection(legacy)
    expect(predicate.contains("_id='20'") && predicate.contains("sub_id='3'") && predicate.contains("numeric='25701'"))
    expect(LocalPbApnRules.rowSelection(legacy+mapOf("name" to "a' OR 1=1 --")).contains("name='a'' OR 1=1 --'"))
    rejects { LocalPbApnRules.rowSelection(legacy-mapOf("_id" to "20").keys) }
    rejects { LocalPbApnRules.rowSelection(legacy+mapOf("unexpected" to "value")) }
    rejects { LocalPbApnRules.rowSelection(legacy+mapOf("name" to "bad\nname")) }
    var store=base+legacy+legacyMms+duplicate+duplicateMms+serviceDuplicates
    var preferred:Long?=20
    val deleted=mutableListOf<Long>()
    var selections=0
    fun run()=LocalPbApnCleanup.run(3,read={store},preferred={preferred},select={ preferred=it;selections++ },delete={ r ->
        expect(preferred==10L && LocalPbApnRules.missing(store,3).isEmpty())
        deleted+=r.getValue("_id").toLong();store=store.filter { it!=r }
    },mapped={})
    expect(run())
    expect(preferred==10L && selections==1 && deleted==listOf(20L,21L,30L,31L))
    expect(store==base+serviceDuplicates)
    expect(!run() && deleted.size==4)
    // A failed preferred-APN write cannot reach deletion.
    preferred=20;store=base+legacy;var writes=0
    rejects { LocalPbApnCleanup.run(3,{store},{preferred},{},{writes++},{}) }
    expect(writes==0)
    // A lost/ineffective delete is an error; it is never repeated automatically.
    preferred=10
    rejects { LocalPbApnCleanup.run(3,{store},{preferred},{preferred=it},{writes++},{}) }
    expect(writes==1 && legacy in store)
    // Changing any snapshot field or the selected SIM aborts before deletion.
    var reads=0;writes=0
    rejects { LocalPbApnCleanup.run(3,{
        reads++; if(reads==2) store=store.map { if(it["_id"]=="20") it+mapOf("name" to "changed") else it };store
    },{preferred},{preferred=it},{writes++},{}) }
    expect(writes==0)
    store=base+legacy
    rejects { LocalPbApnCleanup.run(3,{store},{preferred},{preferred=it},{writes++},{error("MAPPING_CHANGED")}) }
    expect(writes==0)
    // A concurrent new row must not be silently discarded or followed by another deletion.
    store=base+legacy+legacyMms;writes=0
    rejects { LocalPbApnCleanup.run(3,{store},{preferred},{preferred=it},{r ->
        writes++;store=store.filter { it!=r }+listOf(legacy+mapOf("_id" to "99"))
    },{}) }
    expect(writes==1 && store.any { it["_id"]=="21" } && store.any { it["_id"]=="99" })
    // Copies differing only in known/unknown A1 carrier ID or editability are duplicates too.
    expect(ids(base+listOf(duplicate+mapOf("carrier_id" to "-1")))==listOf(30))
    expect(ids(base+listOf(duplicate+mapOf("user_editable" to "1")))==listOf(30))
    store=base.map { if(it["apn"]=="internet") it+mapOf("user_editable" to "1") else it }+duplicate
    preferred=10;deleted.clear();var locks=0
    expect(!LocalPbApnCleanup.run(3,{store},{preferred},{preferred=it},{r ->
        // Selected editable Internet is preserved; conflicting read-only copy goes first.
        deleted+=r.getValue("_id").toLong();store=store.filter { it!=r }
    },{},lock={r ->
        expect(deleted==listOf(30L));locks++;store=store.map { if(it==r) it+mapOf("user_editable" to "0") else it }
    }))
    expect(locks==1 && LocalPbApnRules.missing(store,3).isEmpty())
    // Read-only update refusal is detected by effective APN readback.
    store=base.map { if(it["apn"]=="mms") it+mapOf("user_editable" to "1") else it }
    rejects { LocalPbApnCleanup.run(3,{store},{preferred},{preferred=it},{error("Unexpected delete")},{},lock={}) }
    // Regression from the actual Pixel export: conventional APNs use owned_by=1.
    // Fields absent from this restricted export are filled from the known profile fixture.
    val device=java.io.File(requireNotNull(System.getProperty("local.apn.fixture"))).readLines().filter { it.isNotBlank() }.map { line ->
        val exported=line.substringAfter(" ").substringAfter(" ").split(Regex(", (?=[a-z_]+=)")).associate { field ->
            field.substringBefore('=') to field.substringAfter('=')
        }
        val name=exported.getValue("apn")
        val d=LocalPbApnRules.definitions.firstOrNull { it.apn==name } ?: if(name=="mms.velcom.by") LocalPbApnRules.definitions[1] else LocalPbApnRules.definitions[0]
        row(d,exported.getValue("_id").toInt())+exported
    }
    expect(device.size==6 && device.all { it["owned_by"]=="1" })
    expect(LocalPbApnRules.readOnlyUpdates(device,3)==listOf(399L,400L))
    expect(LocalPbApnRules.xcapVisibilityUpdates(device,3)==listOf(402L))
    var actual=device.map { if(it["apn"]=="xcap") it+mapOf("user_visible" to "0") else it }
    val otherSim=actual.filter { it["sub_id"]=="2" }
    expect(LocalPbApnCleanup.candidates(actual,3,399).isEmpty())
    var actualLocks=0
    expect(!LocalPbApnCleanup.run(3,{actual},{399L},{error("Already preferred")},{error("No selected-SIM duplicates")},{},lock={ r ->
        actualLocks++;actual=actual.map { if(it==r) it+mapOf("user_editable" to "0") else it }
    }))
    expect(actualLocks==2 && LocalPbApnRules.missing(actual,3).isEmpty())
    expect(actual.filter { it["sub_id"]=="2" }==otherSim)
    for(owner in listOf("0","2","","-1")) {
        expect(!LocalPbApnRules.manageable(internet+mapOf("owned_by" to owner),3))
        // Locked DPC/unknown records must be rejected too, before any preferred-APN change.
        rejects { LocalPbApnRules.readOnlyUpdates(listOf(internet+mapOf("owned_by" to owner)),3) }
    }
    // Selected SIM membership, not the stale stored sub_id, identifies visible Velcom records.
    val visibleLegacy=setOf(410L,411L)
    expect(LocalPbApnCleanup.candidates(actual,3,399,visibleLegacy).map { it["_id"] }==listOf("410","411"))
    expect(LocalPbApnCleanup.candidates(actual,3,399,setOf(410L)).map { it["_id"] }==listOf("410"))
    var batchCalls=0;var visibleCalls=0
    val working=actual.filter { it["sub_id"]=="3" }
    expect(!LocalPbApnCleanup.run(3,{actual},{399L},{error("Already preferred")},{error("Batch expected")},{},
        lock={error("Already locked")},visible={visibleCalls++;visibleLegacy},deleteBatch={ batch ->
            expect(batch.map { it["_id"] }==listOf("410","411"));batchCalls++
            val doomed=batch.map { it["_id"] }.toSet();actual=actual.filter { it["_id"] !in doomed }
        }))
    expect(batchCalls==1 && visibleCalls==2 && actual==working)
    var rowReads=0;var preferredReads=0
    expect(!LocalPbApnCleanup.run(3,{rowReads++;actual},{preferredReads++;399L},{error("No selection")},{error("No deletion")},{},
        visible={error("No legacy rows: no extra SIM-view query")},deleteBatch={error("No batch")}))
    expect(rowReads==2 && preferredReads==2) // Previous no-op path repeated full table reads four times.
    actual=working+otherSim;batchCalls=0;visibleCalls=0
    rejects { LocalPbApnCleanup.run(3,{actual},{399L},{},{},{},visible={
        visibleCalls++;if(visibleCalls==1) visibleLegacy else setOf(411L)
    },deleteBatch={batchCalls++}) }
    expect(batchCalls==0) // APN vanished from selected SIM view before deletion.
    actual=working+otherSim;batchCalls=0
    rejects { LocalPbApnCleanup.run(3,{actual},{399L},{},{},{},visible={visibleLegacy},deleteBatch={ batch ->
        batchCalls++;actual=actual.filter { it["_id"]!=batch.first()["_id"] }
    }) }
    expect(batchCalls==1 && actual.any { it["_id"]=="411" }) // Partial provider deletion is detected, no retry.
    actual=working+otherSim;rowReads=0;batchCalls=0
    rejects { LocalPbApnCleanup.run(3,{
        rowReads++;if(rowReads==2) actual=actual.map { if(it["_id"]=="410") it+mapOf("name" to "changed") else it };actual
    },{399L},{},{},{},visible={visibleLegacy},deleteBatch={batchCalls++}) }
    expect(batchCalls==0)
    for(modified in listOf(
        otherSim[0]+mapOf("type" to "default,ims"),otherSim[0]+mapOf("owned_by" to "0"),
        otherSim[0]+mapOf("carrier_id" to "1659"),otherSim[0]+mapOf("mvno_type" to "imsi"))) {
        expect(LocalPbApnCleanup.candidates(working+modified,3,399,visibleLegacy).isEmpty())
    }
    expect(LocalPbApnRules.visibleIds("Row: 0 _id=410\nRow: 1 _id=411\n")==visibleLegacy)
    expect(LocalPbApnRules.visibleIds("No result found.").isEmpty())
    for(invalid in listOf("", "Permission Denial", "Row: 0 _id=-1", "Row: 0 _id=1\nRow: 1 _id=1", "Row: 0 _id=1, apn=x")) {
        rejects { LocalPbApnRules.visibleIds(invalid) }
    }
    val batchWhere=LocalPbApnRules.deletionSelection(otherSim)
    expect(batchWhere.contains("_id='410'") && batchWhere.contains("_id='411'") && batchWhere.contains(" OR "))
    expect(batchWhere.contains("sub_id='2'") && batchWhere.contains("numeric='25701'"))
    rejects { LocalPbApnRules.deletionSelection(emptyList()) }
    rejects { LocalPbApnRules.deletionSelection(listOf(otherSim[0],otherSim[0])) }
    return count
}

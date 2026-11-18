import com.example.simregionswitcher.*

fun mtsByChecks():Int {
    var count=0
    fun expect(value:Boolean) { check(value) { "MTS BY case ${count+1}" };count++ }
    fun rejects(action:()->Unit) { expect(runCatching(action).isFailure) }
    val bytes=java.io.File(requireNotNull(System.getProperty("local.mts.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==MtsByRules.SHA256)
    val profile=LocalPbRules.decode(bytes)
    expect(profile.name=="mts_by" && profile.version==1L)
    expect(profile.flags==MtsByRules.expectedFlags && profile.flags.size==5)
    expect(profile.flags["carrier_wfc_ims_available_bool"]==false)
    expect(profile.integers==MtsByRules.expectedIntegers && profile.integers["carrier_ussd_method_int"]==1)
    expect(profile.strings==MtsByRules.expectedStrings)
    expect(profile.strings.values.none { it.contains("mnc001") })
    for(i in bytes.indices) rejects { LocalPbRules.decode(bytes.copyOf(i)) }
    rejects { LocalPbRules.decode(bytes+byteArrayOf(8,1)) }
    val plan=RegionPlan(1,9,"MTS","257","02",false,"257","02",configMode=MtsByRules.APPLY)
    expect(plan.valid() && BundledProfiles.canApply(plan))
    expect(BundledProfiles.canApply(plan.copy(configMode=MtsByRules.CLEAR)))
    expect(CarrierConfigWritePolicy.writes(plan)==listOf(CarrierConfigWritePolicy.Write(true,false),CarrierConfigWritePolicy.Write(false,false)))
    expect(CarrierConfigWritePolicy.writes(plan.copy(configMode=MtsByRules.CLEAR))==listOf(CarrierConfigWritePolicy.Write(true,false)))
    for(mcc in listOf("257","250","260")) for(mnc in listOf("01","02","03","001")) {
        val matched=mcc=="257" && mnc=="02"
        expect(BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc=mcc,mnc=mnc))==matched)
        expect(!BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc="222",mnc="88")))
    }
    expect(!BundledProfiles.canApply(plan.copy(configMode=LocalPbRules.APPLY)))
    expect(!BundledProfiles.canApply(plan.copy(configMode=LocalPbRules.CLEAR)))
    expect(!BundledProfiles.canApply(plan.copy(persistConfig=true)))
    expect(!BundledProfiles.canApply(plan.copy(reset=true)))
    val expected:Map<String,Any> = profile.flags+profile.integers+profile.strings
    expect(ProfileOverviewRules.configVerified(expected,true))
    expect(!ProfileOverviewRules.configVerified(expected,false))
    for(key in expected.keys) expect(!ProfileOverviewRules.configVerified(expected-key,true))
    val defs=MtsByApnRules.definitions
    expect(defs.map { it.apn }==listOf("mts","mts","ims","xcap"))
    expect(defs.take(2).all { it.auth==1 && it.user=="mts" && it.password=="mts" && it.protocol=="IP" })
    expect(defs[1].mmsc=="http://mmsc" && defs[1].mmsproxy=="192.168.192.168" && defs[1].mmsport=="8080")
    expect(defs[2].visible==0 && defs[2].protocol=="IPV4V6" && defs[3].visible==1)
    fun row(d:LocalPbApnRules.Definition,id:Int)=LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"numeric" to "25702","sub_id" to "9","apn" to d.apn,"type" to d.type,"authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to d.protocol,"roaming_protocol" to d.protocol,"bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"user_editable" to d.editable.toString(),"carrier_enabled" to "1","user" to d.user,"password" to d.password,"mmsproxy" to d.mmsproxy,"mmsport" to d.mmsport)
    val rows=defs.mapIndexed { i,d -> row(d,100+i) }
    expect(MtsByApnRules.missing(rows,9).isEmpty())
    expect(MtsByApnRules.missing(rows,2)==defs)
    expect(MtsByApnRules.missing(emptyList(),9)==defs)
    expect(MtsByApnRules.internetId(rows,9,null)==100L)
    expect(MtsByApnRules.internetId(rows,9,101L)==100L) // MMS shares APN name but is not Internet.
    expect(MtsByApnRules.internetId(rows+row(defs[0],900),9,900L)==900L)
    rejects { MtsByApnRules.missing(rows.map { it+mapOf("numeric" to "25701") },9) }
    rejects { MtsByApnRules.missing(rows.mapIndexed { i,x -> if(i==1) x+mapOf("mmsproxy" to "192.168.192.192") else x },9) }
    rejects { MtsByApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf("type" to "default,supl,mms") else x },9) }
    for(key in listOf("user","password","protocol","roaming_protocol","authtype","proxy","port")) {
        rejects { MtsByApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf(key to "incorrect") else x },9) }
    }
    expect(LocalPbApnRules.definitions.first().apn=="internet" && LocalPbApnRules.definitions.first().editable==0)
    return count
}

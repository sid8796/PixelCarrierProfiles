import com.example.simregionswitcher.*

fun mtsRuChecks():Int {
    var count=0
    fun expect(value:Boolean) { check(value) { "MTS RU case ${count+1}" };count++ }
    fun rejects(action:()->Unit) { expect(runCatching(action).isFailure) }
    val bytes=java.io.File(requireNotNull(System.getProperty("local.mts.ru.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==MtsRuRules.SHA256)
    val profile=LocalPbRules.decode(bytes)
    expect(profile.name=="mts_ru" && profile.version==2L)
    expect(profile.flags==MtsRuRules.expectedFlags && profile.flags.size==6)
    expect(profile.integers==MtsRuRules.expectedIntegers && profile.strings.isEmpty())
    expect(profile.integers["carrier_ussd_method_int"]==1)
    expect(profile.flags["imssms.sms_over_ims_supported_bool"]==true)
    expect(profile.flags["carrier_wfc_ims_available_bool"]==true && profile.flags["carrier_vt_available_bool"]==true)
    for(i in bytes.indices) rejects { LocalPbRules.decode(bytes.copyOf(i)) }
    rejects { LocalPbRules.decode(bytes+byteArrayOf(8,1)) }
    val plan=RegionPlan(0,7,"MTS RU","250","01",false,"250","01",configMode=MtsRuRules.APPLY)
    expect(plan.valid() && BundledProfiles.canApply(plan))
    expect(BundledProfiles.canApply(plan.copy(configMode=MtsRuRules.CLEAR)))
    expect(BundledProfiles.find("250","01")?.id=="mts_ru")
    expect(BundledProfiles.forMode(plan.configMode)?.id==profile.name)
    expect(CarrierConfigWritePolicy.writes(plan)==listOf(CarrierConfigWritePolicy.Write(true,false),CarrierConfigWritePolicy.Write(false,false)))
    expect(CarrierConfigWritePolicy.writes(plan.copy(configMode=MtsRuRules.CLEAR))==listOf(CarrierConfigWritePolicy.Write(true,false)))
    for(mcc in listOf("250","257","260")) for(mnc in listOf("01","02","03","001")) {
        val match=mcc=="250" && mnc=="01"
        for(mode in listOf(MtsRuRules.APPLY,MtsRuRules.CLEAR)) {
            expect(BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc=mcc,mnc=mnc,configMode=mode))==match)
            expect(!BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc="222",mnc="88",configMode=mode)))
        }
    }
    for(mode in listOf(LocalPbRules.APPLY,LocalPbRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR)) expect(!BundledProfiles.canApply(plan.copy(configMode=mode)))
    expect(!BundledProfiles.canApply(plan.copy(persistConfig=true)))
    expect(!BundledProfiles.canApply(plan.copy(reset=true)))
    expect(!BundledProfiles.canApply(plan.copy(subId=-1)))
    expect(!BundledProfiles.canApply(plan.copy(slot=2)))
    val effective:Map<String,Any> = profile.flags+profile.integers
    expect(ProfileOverviewRules.configVerified(effective,ru=true))
    expect(!ProfileOverviewRules.configVerified(effective-"carrier_ussd_method_int",ru=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("carrier_ussd_method_int" to 0),ru=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("carrier_ussd_method_int" to true),ru=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("imssms.sms_over_ims_supported_bool" to false),ru=true))
    expect(BundledProfiles.forMode(plan.configMode)?.experimental==true)
    expect(BundledProfiles.find("257","01")?.experimental==false)
    expect(BundledProfiles.find("257","02")?.experimental==false)
    val old=plan.copy(configMode=MtsRuRules.PREVIOUS_APPLY)
    expect(old.valid())
    expect(!BundledProfiles.canApply(old))
    expect(!LocalPbRules.canSubmitMode(old.configMode))
    expect(!ProfileOverviewRules.configVerified(profile.flags))
    expect(!ProfileOverviewRules.configVerified(profile.flags,mts=true))
    for(key in profile.flags.keys) expect(!ProfileOverviewRules.configVerified(effective-key,ru=true))
    val defs=MtsRuApnRules.definitions
    expect(defs.map { it.apn }==listOf("internet.mts.ru","mms.mts.ru","ims"))
    expect(defs.take(2).all { it.auth==1 && it.user=="mts" && it.password=="mts" })
    expect(defs[0].protocol=="IPV4V6" && defs[1].protocol=="IP")
    expect(defs[1].mmsproxy=="192.168.192.192" && defs[1].mmsport=="8080")
    expect(defs[2].visible==0 && defs[2].auth==-1 && defs[2].bearer==0)
    fun row(d:LocalPbApnRules.Definition,id:Int)=LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"numeric" to "25001","sub_id" to "7","apn" to d.apn,"type" to d.type,"authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to d.protocol,"roaming_protocol" to d.protocol,"bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"user_editable" to d.editable.toString(),"carrier_enabled" to "1","user" to d.user,"password" to d.password,"mmsproxy" to d.mmsproxy,"mmsport" to d.mmsport)
    val rows=defs.mapIndexed { i,d -> row(d,200+i) }
    expect(MtsRuApnRules.missing(rows,7).isEmpty())
    expect(MtsRuApnRules.missing(rows,9)==defs)
    expect(MtsRuApnRules.missing(emptyList(),7)==defs)
    expect(MtsRuApnRules.internetId(rows,7,null)==200L)
    expect(MtsRuApnRules.internetId(rows+row(defs[0],900),7,900L)==900L)
    rejects { MtsRuApnRules.missing(rows.map { it+mapOf("numeric" to "25702") },7) }
    rejects { MtsByApnRules.missing(rows,7) }
    rejects { LocalPbApnRules.missing(rows,7) }
    for(key in listOf("user","password","protocol","roaming_protocol","authtype","proxy","port")) {
        rejects { MtsRuApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf(key to "incorrect") else x },7) }
    }
    rejects { MtsRuApnRules.missing(rows.mapIndexed { i,x -> if(i==1) x+mapOf("mmsproxy" to "192.168.192.168") else x },7) }
    return count
}

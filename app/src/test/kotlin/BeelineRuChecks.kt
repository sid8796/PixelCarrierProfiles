import com.example.simregionswitcher.*

fun beelineRuChecks():Int {
    var count=0
    fun expect(value:Boolean) { check(value) { "Beeline RU case ${count+1}" };count++ }
    fun rejects(action:()->Unit) { expect(runCatching(action).isFailure) }
    val bytes=java.io.File(requireNotNull(System.getProperty("local.beeline.ru.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==BeelineRuRules.SHA256)
    val profile=LocalPbRules.decode(bytes)
    expect(profile.name=="beeline_ru" && profile.version==1L)
    expect(profile.flags==BeelineRuRules.expectedFlags && profile.flags.size==6)
    expect(profile.integers==BeelineRuRules.expectedIntegers && profile.strings==BeelineRuRules.expectedStrings)
    expect(profile.integers["carrier_ussd_method_int"]==1)
    expect(profile.flags["imssms.sms_over_ims_supported_bool"]==true)
    expect(profile.flags["carrier_wfc_ims_available_bool"]==true && "carrier_vt_available_bool" !in profile.flags)
    for(i in bytes.indices) rejects { LocalPbRules.decode(bytes.copyOf(i)) }
    rejects { LocalPbRules.decode(bytes+byteArrayOf(8,1)) }
    val plan=RegionPlan(0,7,"Beeline RU","250","99",false,"250","99",configMode=BeelineRuRules.APPLY)
    expect(plan.valid() && BundledProfiles.canApply(plan))
    expect(BundledProfiles.canApply(plan.copy(configMode=BeelineRuRules.CLEAR)))
    expect(BundledProfiles.find("250","99")?.id=="beeline_ru")
    expect(BundledProfiles.forMode(plan.configMode)?.id==profile.name)
    expect(CarrierConfigWritePolicy.writes(plan)==listOf(CarrierConfigWritePolicy.Write(true,false),CarrierConfigWritePolicy.Write(false,false)))
    expect(CarrierConfigWritePolicy.writes(plan.copy(configMode=BeelineRuRules.CLEAR))==listOf(CarrierConfigWritePolicy.Write(true,false)))
    for(mcc in listOf("250","257","260")) for(mnc in listOf("99","01","02","03","001")) {
        val match=mcc=="250" && mnc=="99"
        for(mode in listOf(BeelineRuRules.APPLY,BeelineRuRules.CLEAR)) {
            expect(BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc=mcc,mnc=mnc,configMode=mode))==match)
            expect(!BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc="222",mnc="88",configMode=mode)))
        }
    }
    for(mode in listOf(LocalPbRules.APPLY,LocalPbRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR)) expect(!BundledProfiles.canApply(plan.copy(configMode=mode)))
    expect(!BundledProfiles.canApply(plan.copy(persistConfig=true)))
    expect(!BundledProfiles.canApply(plan.copy(reset=true)))
    expect(!BundledProfiles.canApply(plan.copy(subId=-1)))
    expect(!BundledProfiles.canApply(plan.copy(slot=2)))
    val effective:Map<String,Any> = profile.flags+profile.integers+profile.strings
    expect(ProfileOverviewRules.configVerified(effective,beeline=true))
    expect(!ProfileOverviewRules.configVerified(effective-"carrier_ussd_method_int",beeline=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("carrier_ussd_method_int" to 0),beeline=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("carrier_ussd_method_int" to true),beeline=true))
    expect(!ProfileOverviewRules.configVerified(effective+mapOf("imssms.sms_over_ims_supported_bool" to false),beeline=true))
    expect(BundledProfiles.forMode(plan.configMode)?.experimental==true)
    expect(BundledProfiles.find("257","01")?.experimental==false)
    expect(BundledProfiles.find("257","02")?.experimental==false)
    expect(!ProfileOverviewRules.configVerified(profile.flags))
    expect(!ProfileOverviewRules.configVerified(profile.flags,mts=true))
    for(key in profile.flags.keys) expect(!ProfileOverviewRules.configVerified(effective-key,beeline=true))
    val defs=BeelineRuApnRules.definitions
    expect(defs.map { it.apn }==listOf("internet.beeline.ru","mms.beeline.ru","ims","xcap.beeline.ru"))
    expect(defs.take(2).all { it.auth==1 && it.user=="beeline" && it.password=="beeline" })
    expect(defs[0].protocol=="IPV4V6" && defs[1].protocol=="IP")
    expect(defs[1].mmsproxy=="192.168.94.23" && defs[1].mmsport=="8080")
    expect(defs[2].visible==0 && defs[2].auth==-1 && defs[2].bearer==0)
    fun row(d:LocalPbApnRules.Definition,id:Int)=LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"numeric" to "25099","sub_id" to "7","apn" to d.apn,"type" to d.type,"authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to d.protocol,"roaming_protocol" to d.protocol,"bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"user_editable" to d.editable.toString(),"carrier_enabled" to "1","user" to d.user,"password" to d.password,"mmsproxy" to d.mmsproxy,"mmsport" to d.mmsport)
    val rows=defs.mapIndexed { i,d -> row(d,200+i) }
    expect(BeelineRuApnRules.missing(rows,7).isEmpty())
    expect(BeelineRuApnRules.missing(rows,9)==defs)
    expect(BeelineRuApnRules.missing(emptyList(),7)==defs)
    expect(BeelineRuApnRules.internetId(rows,7,null)==200L)
    expect(BeelineRuApnRules.internetId(rows+row(defs[0],900),7,900L)==900L)
    rejects { BeelineRuApnRules.missing(rows.map { it+mapOf("numeric" to "25702") },7) }
    rejects { MtsByApnRules.missing(rows,7) }
    rejects { LocalPbApnRules.missing(rows,7) }
    for(key in listOf("user","password","protocol","roaming_protocol","authtype","proxy","port")) {
        rejects { BeelineRuApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf(key to "incorrect") else x },7) }
    }
    rejects { BeelineRuApnRules.missing(rows.mapIndexed { i,x -> if(i==1) x+mapOf("mmsproxy" to "192.168.192.168") else x },7) }
    expect(defs[2].protocol=="IP" && defs[3].visible==0)
    expect(profile.integers["imsss.ut_as_server_port_int"]==81)
    expect(profile.strings["default_vm_number_string"]=="600")
    expect(ProfileOverviewRules.checkState(effective,beeline=true)==ProfileOverviewRules.CheckState.ACTIVE)
    expect(ProfileOverviewRules.checkState(null,beeline=true)==ProfileOverviewRules.CheckState.NOT_CHECKED)
    expect(ProfileOverviewRules.checkState(effective,error="CONFIG_ACCESS_DENIED",beeline=true)==ProfileOverviewRules.CheckState.FAILED)
    for(key in effective.keys) {
        expect(ProfileOverviewRules.checkState(effective-key,beeline=true)==ProfileOverviewRules.CheckState.DIFFERENT)
        expect(ProfileOverviewRules.differences(effective-key,beeline=true).single().key==key)
    }
    rejects { MtsRuApnRules.missing(rows,7) }
    return count
}

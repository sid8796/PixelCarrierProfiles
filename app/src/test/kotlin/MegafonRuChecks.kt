import com.example.simregionswitcher.*

fun megafonRuChecks():Int {
    var count=0
    fun expect(value:Boolean) { check(value) { "Megafon RU case ${count+1}" };count++ }
    fun rejects(action:()->Unit) { expect(runCatching(action).isFailure) }
    val bytes=java.io.File(requireNotNull(System.getProperty("local.megafon.ru.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==MegafonRuRules.SHA256)
    val profile=LocalPbRules.decode(bytes)
    expect(profile.name=="megafon_ru" && profile.version==2L)
    expect(profile.flags==MegafonRuRules.expectedFlags && profile.flags.size==6)
    expect(profile.integers==MegafonRuRules.expectedIntegers && profile.strings==MegafonRuRules.expectedStrings)
    expect(profile.integers["carrier_ussd_method_int"]==1)
    expect(profile.flags["imssms.sms_over_ims_supported_bool"]==true)
    expect(profile.flags["carrier_wfc_ims_available_bool"]==true && "carrier_vt_available_bool" !in profile.flags)
    for(i in bytes.indices) rejects { LocalPbRules.decode(bytes.copyOf(i)) }
    rejects { LocalPbRules.decode(bytes+byteArrayOf(8,1)) }
    val plan=RegionPlan(0,7,"Megafon RU","250","02",false,"250","02",configMode=MegafonRuRules.APPLY)
    expect(plan.valid() && BundledProfiles.canApply(plan))
    expect(BundledProfiles.canApply(plan.copy(configMode=MegafonRuRules.CLEAR)))
    expect(BundledProfiles.find("250","02")?.id=="megafon_ru")
    expect(BundledProfiles.forMode(plan.configMode)?.id==profile.name)
    expect(CarrierConfigWritePolicy.writes(plan)==listOf(CarrierConfigWritePolicy.Write(true,false),CarrierConfigWritePolicy.Write(false,false)))
    expect(CarrierConfigWritePolicy.writes(plan.copy(configMode=MegafonRuRules.CLEAR))==listOf(CarrierConfigWritePolicy.Write(true,false)))
    for(mcc in listOf("250","257","260")) for(mnc in listOf("99","01","02","03","001")) {
        val match=mcc=="250" && mnc=="02"
        for(mode in listOf(MegafonRuRules.APPLY,MegafonRuRules.CLEAR)) {
            expect(BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc=mcc,mnc=mnc,configMode=mode))==match)
            expect(!BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc="222",mnc="88",configMode=mode)))
        }
    }
    for(mode in listOf(LocalPbRules.APPLY,LocalPbRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR)) expect(!BundledProfiles.canApply(plan.copy(configMode=mode)))
    expect(!BundledProfiles.canApply(plan.copy(persistConfig=true)))
    expect(!BundledProfiles.canApply(plan.copy(reset=true)))
    expect(!BundledProfiles.canApply(plan.copy(subId=-1)))
    expect(!BundledProfiles.canApply(plan.copy(slot=2)))
    fun configVerified(values:Map<String,Any?>)=ProfileOverviewRules.checkStateFor(values,"megafon_ru")==ProfileOverviewRules.CheckState.ACTIVE
    val effective:Map<String,Any> = profile.flags+profile.integers+profile.strings+profile.arrays
    expect(configVerified(effective))
    expect(!configVerified(effective-"carrier_ussd_method_int"))
    expect(!configVerified(effective+mapOf("carrier_ussd_method_int" to 0)))
    expect(!configVerified(effective+mapOf("carrier_ussd_method_int" to true)))
    expect(!configVerified(effective+mapOf("imssms.sms_over_ims_supported_bool" to false)))
    expect(BundledProfiles.forMode(plan.configMode)?.experimental==true)
    expect(BundledProfiles.find("257","01")?.experimental==false)
    expect(BundledProfiles.find("257","02")?.experimental==false)
    expect(!configVerified(profile.flags))
    expect(!ProfileOverviewRules.configVerified(profile.flags,mts=true))
    for(key in profile.flags.keys) expect(!configVerified(effective-key))
    val defs=MegafonRuApnRules.definitions
    expect(defs.map { it.apn }==listOf("internet","mms","ims","hos"))
    expect(defs[0].auth==1 && defs[0].user=="gdata" && defs[0].password=="gdata" && defs[1].user=="mms" && defs[1].password=="mms")
    expect(defs[0].protocol=="IPV4V6" && defs[1].protocol=="IP")
    expect(defs[1].mmsproxy=="10.10.10.10" && defs[1].mmsport=="8080")
    expect(defs[2].visible==0 && defs[2].auth==-1 && defs[2].bearer==0)
    fun row(d:LocalPbApnRules.Definition,id:Int)=LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"numeric" to "25002","sub_id" to "7","apn" to d.apn,"type" to d.type,"authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to d.protocol,"roaming_protocol" to d.protocol,"bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"user_editable" to d.editable.toString(),"carrier_enabled" to "1","user" to d.user,"password" to d.password,"mmsproxy" to d.mmsproxy,"mmsport" to d.mmsport)
    val rows=defs.mapIndexed { i,d -> row(d,200+i) }
    expect(MegafonRuApnRules.missing(rows,7).isEmpty())
    expect(MegafonRuApnRules.missing(rows,9)==defs)
    expect(MegafonRuApnRules.missing(emptyList(),7)==defs)
    expect(MegafonRuApnRules.internetId(rows,7,null)==200L)
    expect(MegafonRuApnRules.internetId(rows+row(defs[0],900),7,900L)==900L)
    rejects { MegafonRuApnRules.missing(rows.map { it+mapOf("numeric" to "25702") },7) }
    rejects { MtsByApnRules.missing(rows,7) }
    rejects { LocalPbApnRules.missing(rows,7) }
    for(key in listOf("user","password","protocol","roaming_protocol","authtype","proxy","port")) {
        rejects { MegafonRuApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf(key to "incorrect") else x },7) }
    }
    rejects { MegafonRuApnRules.missing(rows.mapIndexed { i,x -> if(i==1) x+mapOf("mmsproxy" to "192.168.192.168") else x },7) }
    expect(defs[2].protocol=="IPV4V6" && defs[3].visible==0)
    expect("imsss.ut_as_server_port_int" !in profile.integers)
    expect(profile.strings["xcap_apn_name_string"]=="hos" && profile.strings["iwlan.epdg_static_address_string"]=="epdg.epc.mnc002.mcc250.pub.3gppnetwork.org")
    expect(ProfileOverviewRules.checkStateFor(effective,"megafon_ru")==ProfileOverviewRules.CheckState.ACTIVE)
    expect(ProfileOverviewRules.checkStateFor(null,"megafon_ru")==ProfileOverviewRules.CheckState.NOT_CHECKED)
    expect(ProfileOverviewRules.checkStateFor(effective,"megafon_ru",error="CONFIG_ACCESS_DENIED")==ProfileOverviewRules.CheckState.FAILED)
    for(key in effective.keys) {
        expect(ProfileOverviewRules.checkStateFor(effective-key,"megafon_ru")==ProfileOverviewRules.CheckState.DIFFERENT)
        expect(ProfileOverviewRules.differencesFor(effective-key,"megafon_ru").single().key==key)
    }
    rejects { MtsRuApnRules.missing(rows,7) }
    expect(profile.arrays==mapOf("iwlan.epdg_address_priority_int_array" to listOf(0,1)))
    val retired=plan.copy(configMode=MegafonRuRules.PREVIOUS_APPLY)
    expect(retired.valid() && !BundledProfiles.canApply(retired))
    expect(!LocalPbRules.canSubmitMode(retired.configMode))
    for(bad in listOf(listOf(1,0),listOf(0),listOf(0,1,2),listOf("0","1"),intArrayOf(0,1)))
        expect(ProfileOverviewRules.checkStateFor(effective+mapOf("iwlan.epdg_address_priority_int_array" to bad),"megafon_ru")==ProfileOverviewRules.CheckState.DIFFERENT)
    expect(bytes.takeLast(6).toByteArray().contentEquals(byteArrayOf(58,4,8,0,8,1)))
    // Reject an unapproved discovery method and a malformed array element tag.
    rejects { LocalPbRules.decode(bytes.copyOf().apply { this[lastIndex]=2 }) }
    rejects { LocalPbRules.decode(bytes.copyOf().apply { this[lastIndex-1]=16 }) }
    return count
}

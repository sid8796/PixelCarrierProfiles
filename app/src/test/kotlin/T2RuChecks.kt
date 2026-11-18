import com.example.simregionswitcher.*

fun t2RuChecks():Int {
    var count=0
    fun expect(value:Boolean) { check(value) { "T2 RU case ${count+1}" };count++ }
    fun rejects(action:()->Unit) { expect(runCatching(action).isFailure) }
    val bytes=java.io.File(requireNotNull(System.getProperty("local.t2.ru.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==T2RuRules.SHA256)
    val profile=LocalPbRules.decode(bytes)
    expect(profile.name=="t2_ru" && profile.version==1L)
    expect(profile.flags==T2RuRules.expectedFlags && profile.flags.size==7)
    expect(profile.integers==T2RuRules.expectedIntegers && profile.strings==T2RuRules.expectedStrings)
    expect(profile.integers["carrier_ussd_method_int"]==1)
    expect(profile.flags["imssms.sms_over_ims_supported_bool"]==true)
    expect(profile.flags["carrier_wfc_ims_available_bool"]==true && profile.flags["carrier_vt_available_bool"]==true)
    for(i in bytes.indices) rejects { LocalPbRules.decode(bytes.copyOf(i)) }
    rejects { LocalPbRules.decode(bytes+byteArrayOf(8,1)) }
    val plan=RegionPlan(0,7,"T2 RU","250","20",false,"250","20",configMode=T2RuRules.APPLY)
    expect(plan.valid() && BundledProfiles.canApply(plan))
    expect(BundledProfiles.canApply(plan.copy(configMode=T2RuRules.CLEAR)))
    expect(BundledProfiles.find("250","20")?.id=="t2_ru")
    expect(BundledProfiles.forMode(plan.configMode)?.id==profile.name)
    expect(CarrierConfigWritePolicy.writes(plan)==listOf(CarrierConfigWritePolicy.Write(true,false),CarrierConfigWritePolicy.Write(false,false)))
    expect(CarrierConfigWritePolicy.writes(plan.copy(configMode=T2RuRules.CLEAR))==listOf(CarrierConfigWritePolicy.Write(true,false)))
    for(mcc in listOf("250","257","260")) for(mnc in listOf("20","99","01","02","03","001")) {
        val match=mcc=="250" && mnc=="20"
        for(mode in listOf(T2RuRules.APPLY,T2RuRules.CLEAR)) {
            expect(BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc=mcc,mnc=mnc,configMode=mode))==match)
            expect(!BundledProfiles.canApply(plan.copy(sourceMcc=mcc,sourceMnc=mnc,mcc="222",mnc="88",configMode=mode)))
        }
    }
    for(mode in listOf(LocalPbRules.APPLY,LocalPbRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR)) expect(!BundledProfiles.canApply(plan.copy(configMode=mode)))
    expect(!BundledProfiles.canApply(plan.copy(persistConfig=true)))
    expect(!BundledProfiles.canApply(plan.copy(reset=true)))
    expect(!BundledProfiles.canApply(plan.copy(subId=-1)))
    expect(!BundledProfiles.canApply(plan.copy(slot=2)))
    fun configVerified(values:Map<String,Any?>)=ProfileOverviewRules.checkStateFor(values,"t2_ru")==ProfileOverviewRules.CheckState.ACTIVE
    val effective:Map<String,Any> = profile.flags+profile.integers+profile.strings
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
    val defs=T2RuApnRules.definitions
    expect(defs.map { it.apn }==listOf("internet","mms","ims","hos"))
    expect(defs.all { it.auth==-1 && it.user.isEmpty() && it.password.isEmpty() } && defs[0].type=="default")
    expect(defs[0].protocol=="IPV4V6" && defs[1].protocol=="IP")
    expect(defs[1].mmsproxy=="193.12.40.65" && defs[1].mmsport=="8080")
    expect(defs[2].visible==0 && defs[2].auth==-1 && defs[2].bearer==0)
    fun row(d:LocalPbApnRules.Definition,id:Int)=LocalPbApnRules.columns.associateWith { "" }+mapOf(
        "_id" to id.toString(),"numeric" to "25020","sub_id" to "7","apn" to d.apn,"type" to d.type,"authtype" to d.auth.toString(),"mmsc" to d.mmsc,"protocol" to d.protocol,"roaming_protocol" to d.protocol,"bearer_bitmask" to d.bearer.toString(),"user_visible" to d.visible.toString(),"user_editable" to d.editable.toString(),"carrier_enabled" to "1","user" to d.user,"password" to d.password,"mmsproxy" to d.mmsproxy,"mmsport" to d.mmsport)
    val rows=defs.mapIndexed { i,d -> row(d,200+i) }
    expect(T2RuApnRules.missing(rows,7).isEmpty())
    expect(T2RuApnRules.missing(rows,9)==defs)
    expect(T2RuApnRules.missing(emptyList(),7)==defs)
    expect(T2RuApnRules.internetId(rows,7,null)==200L)
    expect(T2RuApnRules.internetId(rows+row(defs[0],900),7,900L)==900L)
    rejects { T2RuApnRules.missing(rows.map { it+mapOf("numeric" to "25702") },7) }
    rejects { MtsByApnRules.missing(rows,7) }
    rejects { LocalPbApnRules.missing(rows,7) }
    for(key in listOf("user","password","protocol","roaming_protocol","authtype","proxy","port")) {
        rejects { T2RuApnRules.missing(rows.mapIndexed { i,x -> if(i==0) x+mapOf(key to "incorrect") else x },7) }
    }
    rejects { T2RuApnRules.missing(rows.mapIndexed { i,x -> if(i==1) x+mapOf("mmsproxy" to "192.168.192.168") else x },7) }
    expect(defs[2].protocol=="IPV4V6" && defs[3].visible==0)
    expect(profile.integers["imsss.ut_as_server_port_int"]==80)
    expect(profile.strings["xcap_apn_name_string"]=="hos" && profile.strings["imsss.ut_as_server_fqdn_string"]=="xcap.ims.mnc020.mcc250.pub.3gppnetwork.org")
    expect(ProfileOverviewRules.checkStateFor(effective,"t2_ru")==ProfileOverviewRules.CheckState.ACTIVE)
    expect(ProfileOverviewRules.checkStateFor(null,"t2_ru")==ProfileOverviewRules.CheckState.NOT_CHECKED)
    expect(ProfileOverviewRules.checkStateFor(effective,"t2_ru",error="CONFIG_ACCESS_DENIED")==ProfileOverviewRules.CheckState.FAILED)
    for(key in effective.keys) {
        expect(ProfileOverviewRules.checkStateFor(effective-key,"t2_ru")==ProfileOverviewRules.CheckState.DIFFERENT)
        expect(ProfileOverviewRules.differencesFor(effective-key,"t2_ru").single().key==key)
    }
    rejects { MtsRuApnRules.missing(rows,7) }
    expect(profile.integers["bsf.bsf_server_port_int"]==80)
    expect(profile.strings["bsf.bsf_server_fqdn_string"]=="bsf.ims.mnc020.mcc250.pub.3gppnetwork.org")
    expect("iwlan.epdg_static_address_string" !in profile.strings && profile.arrays.isEmpty())
    expect(defs[1].mmsc=="http://mms")
    for(mode in listOf(BeelineRuRules.APPLY,MegafonRuRules.APPLY,MtsRuRules.APPLY))
        expect(!BundledProfiles.canApply(plan.copy(configMode=mode)))
    rejects { BeelineRuApnRules.missing(rows,7) }
    rejects { MegafonRuApnRules.missing(rows,7) }
    return count
}

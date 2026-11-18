import com.example.simregionswitcher.*

fun localPbChecks(): Int {
    var count=0
    fun expect(value: Boolean) { check(value) { "local pb check ${count+1}" };count++ }
    fun rejects(action: () -> Unit) { expect(runCatching(action).isFailure) }
    fun number(n: Int): ByteArray {
        var value=n;val bytes=mutableListOf<Byte>()
        do { val next=value and 127;value=value ushr 7;bytes.add((next or if(value!=0) 128 else 0).toByte()) } while(value!=0)
        return bytes.toByteArray()
    }
    fun message(tag: Int,bytes: ByteArray)=number(tag*8+2)+number(bytes.size)+bytes
    fun integer(tag: Int,value: Int)=number(tag*8)+number(value)
    fun entry(key: String,value: Int)=message(2,message(1,key.toByteArray())+integer(5,value))
    
    val fixture=java.io.File(requireNotNull(System.getProperty("local.pb.fixture"))).readBytes()
    expect(java.security.MessageDigest.getInstance("SHA-256").digest(fixture).joinToString("") { "%02x".format(it.toInt() and 255) }==LocalPbProfile.SHA256)
    // Exact APN section reconstructed independently from the approved settings.
    fun text(f:Int,v:String)=message(f,v.toByteArray())
    val apns=message(3,
        message(2,text(1,"A1 BY Internet")+text(2,"internet")+integer(3,1)+integer(3,3)+integer(10,1)+integer(14,2)+integer(15,2)+integer(24,0))+
        message(2,text(1,"A1 BY MMS")+text(2,"mms")+integer(3,2)+integer(10,1)+text(11,"http://mmsc")+integer(14,2)+integer(15,2)+integer(24,0))+
        message(2,text(1,"A1 BY IMS")+text(2,"ims")+integer(3,7)+text(4,"14|18|20")+integer(14,2)+integer(15,2)+integer(23,0))+
        message(2,text(1,"A1 BY XCAP")+text(2,"xcap")+integer(3,11)+integer(14,2)+integer(15,2)+integer(23,0)))
    val serverEntry=message(2,text(1,LocalPbRules.XCAP_SERVER_KEY)+text(2,LocalPbRules.XCAP_SERVER))
    val apnEntry=message(2,text(1,LocalPbRules.XCAP_APN_KEY)+text(2,LocalPbRules.XCAP_APN))
    val bsfEntry=message(2,text(1,LocalPbRules.BSF_KEY)+text(2,LocalPbRules.BSF_SERVER))
    val bsfPort=message(2,text(1,LocalPbRules.BSF_PORT_KEY)+integer(3,8080))
    val voicemail=message(2,text(1,LocalPbRules.VOICEMAIL_KEY)+text(2,LocalPbRules.VOICEMAIL_NUMBER))
    val server=serverEntry+apnEntry+bsfEntry+bsfPort+voicemail
    fun profile(config: ByteArray,name: String="a1_by",version: Int=16)=message(1,name.toByteArray())+integer(2,version)+apns+message(4,config+message(2,message(1,LocalPbRules.USSD_KEY.toByteArray())+integer(3,1))+server)
    val keys=LocalPbRules.keys
    val config=keys.map { entry(it,if(LocalPbRules.expectedFlags.getValue(it)) 1 else 0) }.reduce { a,b -> a+b }
    val valid=profile(config)
    expect(LocalPbRules.decode(valid).flags==LocalPbRules.expectedFlags)
    expect(fixture.contentEquals(valid))
    expect(LocalPbRules.decode(fixture).flags==LocalPbRules.expectedFlags)
    expect(LocalPbRules.decode(profile(keys.map { entry(it,0) }.reduce { a,b -> a+b })).flags.values.none { it })
    rejects { LocalPbRules.decode(byteArrayOf()) }
    rejects { LocalPbRules.decode(ByteArray(4097)) }
    for(i in valid.indices) rejects { LocalPbRules.decode(valid.copyOf(i)) }
    rejects { LocalPbRules.decode(valid+integer(9,1)) }
    rejects { LocalPbRules.decode(valid+message(1,"a1_by".toByteArray())) }
    rejects { LocalPbRules.decode(profile(config,"windtre_it")) }
    rejects { LocalPbRules.decode(profile(config,version=3)) }
    rejects { LocalPbRules.decode(profile(keys.map { entry(it,2) }.reduce { a,b -> a+b })) }
    rejects { LocalPbRules.decode(profile(keys.mapIndexed { i,k -> entry(if(i==1) keys[0] else k,1) }.reduce { a,b -> a+b })) }
    rejects { LocalPbRules.decode(profile(keys.mapIndexed { i,k -> entry(if(i==1) "carrier_certificate_string_array" else k,1) }.reduce { a,b -> a+b })) }
    rejects { LocalPbRules.decode(profile(config)+message(3,byteArrayOf())) } // Duplicate APN section rejected.
    rejects { LocalPbRules.decode(valid+byteArrayOf(0)) }
    rejects { LocalPbRules.decode(ByteArray(12) { 0x80.toByte() }) }
    rejects { LocalPbRules.decode(profile(config,version=1)) }
    rejects { LocalPbRules.decode(profile(config+entry("imsvoice.evs_supported_bool",1))) }
    rejects { LocalPbRules.decode(profile(keys.take(3).map { entry(it,1) }.reduce { a,b -> a+b })) }
    rejects { LocalPbRules.decode(valid.map { if(it=='I'.code.toByte()) 'X'.code.toByte() else it }.toByteArray()) }
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.PREVIOUS_APPLY))
    val plain=RegionPlan(0,3,"A1 BY","257","01",false,"257","01")
    val local=plain.copy(configMode=LocalPbRules.APPLY)
    val clear=plain.copy(configMode=LocalPbRules.CLEAR)
    expect(plain.valid() && local.valid() && clear.valid())
    expect(plain.copy(configMode=LocalPbRules.LEGACY_APPLY).valid()) // Historical v1 plans still decode.
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.LEGACY_APPLY)) // Never apply v2 bytes under v1 authorization.
    expect(LocalPbRules.canSubmitMode(LocalPbRules.APPLY) && LocalPbRules.canSubmitMode(LocalPbRules.CLEAR))
    expect(local.copy(slot=1,subId=2,embedded=true).valid())
    expect(!local.copy(slot=2).valid())
    expect(!local.copy(subId=-1).valid())
    expect(!local.copy(sourceMcc="260",sourceMnc="03",mcc="260",mnc="03").valid())
    expect(!local.copy(mcc="222",mnc="88").valid())
    expect(!local.copy(reset=true).valid())
    expect(!local.copy(configMode="arbitrary-file").valid())
    rejects { RegionActionRules.modifierArguments(local) }
    rejects { RegionActionRules.modifierArguments(clear) }
    rejects { ProfileApnFollowUp<String>().arm("request",local,"snapshot") }
    LocalCarrierAuthorization.arm("pb-test",local)
    expect(!LocalCarrierAuthorization.consume("pb-test",clear))
    expect(!LocalCarrierAuthorization.consume("pb-test",local.copy(subId=2,slot=1)))
    expect(LocalCarrierAuthorization.consume("pb-test",local))
    expect(!LocalCarrierAuthorization.consume("pb-test",local))
    val trace=OperationTrace(0,3,"257","01",false,false,1000,1000,LocalPbRules.APPLY)
    trace.record("START",1000,1000)
    expect(!trace.executionObserved) // Old terminal result must not finish this new request.
    trace.record("LOCAL_PB_APPLY",1100,1100)
    expect(trace.executionObserved)
    val definition=LocalPbApnRules.definitions.first()
    fun apnRow(id:String="1",auth:String="1",type:String="default,supl",proxy:String="NULL"):String {
        val values=mapOf("_id" to id,"apn" to "internet","numeric" to "25701","sub_id" to "3","type" to type,"authtype" to auth,"mmsc" to "NULL","protocol" to "IPV4V6","roaming_protocol" to "IPV4V6","bearer_bitmask" to "0","user_visible" to "1","carrier_enabled" to "1","proxy" to proxy,"user_editable" to "0","name" to "A1 BY Internet","owned_by" to "1","carrier_id" to "568","apn_set_id" to "0","network_type_bitmask" to "0","edited" to "1")
        return "Row: 0 "+LocalPbApnRules.columns.joinToString(", ") { it+"="+(values[it] ?: "NULL") }
    }
    expect(LocalPbApnRules.parse("No result found.").isEmpty())
    expect(!LocalPbApnRules.exists(emptyList(),definition))
    expect(LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow()),definition))
    rejects { LocalPbApnRules.parse("") }
    rejects { LocalPbApnRules.parse("java.lang.SecurityException: Permission Denial") }
    rejects { LocalPbApnRules.parse("Error while accessing provider") }
    rejects { LocalPbApnRules.parse("No result found.\nPermission Denial") }
    rejects { LocalPbApnRules.parse("x".repeat(100001)) }
    rejects { LocalPbApnRules.parse(apnRow()+"\n"+apnRow()) }
    rejects { LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow(auth="0")),definition) }
    rejects { LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow(proxy="10.0.0.1")),definition) }
    rejects { LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow(type="*")),definition) }
    expect(!LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow(type="dun")),definition))
    rejects { LocalPbApnRules.exists(LocalPbApnRules.parse(apnRow().replace("apn=internet","apn=other")),definition) }
    rejects { LocalPbApnRules.parse(apnRow().replace(", password=NULL","")) }
    expect(LocalPbApnRules.selection=="numeric=25701")
    val created=LocalPbApnRules.parse(apnRow(id="390"))
    expect(LocalPbApnRules.exists(LocalPbApnRules.targeted(created,3,"internet"),definition))
    expect(LocalPbApnRules.targeted(created,2,"internet").isEmpty())
    expect(LocalPbApnRules.targeted(created,3,"mms").isEmpty())
    val other=LocalPbApnRules.parse(apnRow(id="391").replace("sub_id=3","sub_id=2"))
    expect(LocalPbApnRules.targeted(created+other,3,"internet").single()["_id"]=="390")
    rejects { LocalPbApnRules.targeted(created,-1,"internet") }
    rejects { LocalPbApnRules.targeted(created,3,"internet' OR 1=1") }
    rejects { LocalPbApnRules.targeted(listOf(created.single()+mapOf("numeric" to "26003")),3,"internet") }
    expect(LocalPbApnRules.escapedBinding("mmsc:s:http://mmsc")=="mmsc:s:http\\://mmsc")
    expect(LocalPbApnRules.escapedBinding("mmsc:s:")=="mmsc:s:")
    expect(LocalPbApnRules.escapedBinding("name:s:A1 BY MMS")=="name:s:A1 BY MMS")
    expect(LocalPbApnRules.escapedBinding("sub_id:i:3")=="sub_id:i:3")
    expect(LocalPbApnRules.commandError("usage: adb shell content\n[ERROR] Binding not well formed: mmsc"))
    expect(LocalPbApnRules.commandError("[ERROR] Unsupported argument"))
    expect(!LocalPbApnRules.commandError(""))
    rejects { LocalPbApnRules.escapedBinding("bad binding") }
    // Independent model of Android splitWithEscaping: the actual value must roundtrip.
    fun splitBinding(value:String):List<String> {
        val out=mutableListOf<String>();val current=StringBuilder();var escape=false
        for(c in value) {
            if(escape) { current.append(c);escape=false }
            else if(c=='\\') escape=true
            else if(c==':') { out.add(current.toString());current.setLength(0) }
            else current.append(c)
        }
        check(!escape);out.add(current.toString());return out
    }
    for(value in listOf("http://mmsc","https://host:8080/path","", "A1 BY MMS", "a\\b:c")) {
        expect(splitBinding(LocalPbApnRules.escapedBinding("mmsc:s:"+value))==listOf("mmsc","s",value))
    }
    expect(LocalPbRules.decode(fixture).integers==LocalPbRules.expectedIntegers)
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.APN_V5))
    expect(LocalPbRules.validMode(LocalPbRules.APN_V5))
    val ussd=message(2,message(1,LocalPbRules.USSD_KEY.toByteArray())+integer(3,1))
    fun replaceUssd(value:Int,tag:Int=3):ByteArray {
        val extra=message(2,message(1,LocalPbRules.USSD_KEY.toByteArray())+integer(tag,value))
        return message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config+extra+server)
    }
    rejects { LocalPbRules.decode(replaceUssd(0)) }
    rejects { LocalPbRules.decode(replaceUssd(2)) }
    rejects { LocalPbRules.decode(replaceUssd(3)) }
    rejects { LocalPbRules.decode(replaceUssd(1,5)) }
    rejects { LocalPbRules.decode(profile(config+ussd)) }
    rejects { LocalPbRules.decode(message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config)) }
    rejects { LocalPbRules.decode(profile(config,version=5)) }
    val overview:Map<String,Any> = LocalPbRules.expectedFlags+LocalPbRules.expectedIntegers+LocalPbRules.expectedStrings
    expect(ProfileOverviewRules.configVerified(overview))
    expect(!ProfileOverviewRules.configVerified(null))
    expect(!ProfileOverviewRules.configVerified(emptyMap()))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.USSD_KEY to true)))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.USSD_KEY to "1")))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.USSD_KEY to 0)))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.keys.first() to false)))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.keys.first() to 1)))
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.keys.first()))
    expect(LocalPbApnRules.missing(created,3).map { it.apn }==listOf("mms","ims","xcap"))
    expect(LocalPbApnRules.missing(emptyList(),3).size==4)
    expect(LocalPbApnRules.internetId(created,3)==390L)
    rejects { LocalPbApnRules.internetId(created,2) }
    expect(LocalPbApnRules.internetId(created+listOf(created.single()+mapOf("_id" to "999")),3)==390L)
    expect(LocalPbApnRules.internetId(created+listOf(created.single()+mapOf("_id" to "999")),3,999L)==999L)
    expect(LocalPbApnRules.preferredId("Row: 0 _id=390\n")==390L)
    expect(LocalPbApnRules.preferredId("No result found.")==null)
    rejects { LocalPbApnRules.preferredId("java.lang.SecurityException") }
    rejects { LocalPbApnRules.preferredId("Row: 0 _id=390\nRow: 1 _id=391") }
    rejects { LocalPbApnRules.preferredId("") }
    expect(ProfileOverviewRules.durationSeconds(null)==null)
    expect(ProfileOverviewRules.durationSeconds(-1)==null)
    expect(ProfileOverviewRules.durationSeconds(Long.MAX_VALUE)==null)
    expect(ProfileOverviewRules.durationSeconds(0)==0L)
    expect(ProfileOverviewRules.durationSeconds(6200)==6L)
    expect(ProfileOverviewRules.durationSeconds(8900)==9L)
    expect(LocalPbRules.decode(fixture).flags["hide_lte_plus_data_icon_bool"]==false)
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.USSD_V6))
    expect(LocalPbRules.validMode(LocalPbRules.USSD_V6))
    rejects { LocalPbRules.decode(profile(config,version=6)) }
    expect(LocalPbRules.validMode(LocalPbRules.ICON_V7))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.ICON_V7))
    rejects { LocalPbRules.decode(profile(config,version=7)) }
    val xcap=LocalPbApnRules.definitions.single { it.apn=="xcap" }
    val xrow=created.single()+mapOf("_id" to "400","apn" to "xcap","type" to "xcap","authtype" to "-1","user_visible" to "0","user_editable" to "1")
    expect(LocalPbApnRules.exists(listOf(xrow),xcap))
    expect(LocalPbApnRules.missing(created+listOf(xrow),3).map { it.apn }==listOf("mms","ims"))
    rejects { LocalPbApnRules.exists(listOf(xrow+mapOf("protocol" to "IP")),xcap) }
    rejects { LocalPbApnRules.exists(listOf(xrow+mapOf("roaming_protocol" to "IP")),xcap) }
    rejects { LocalPbApnRules.exists(listOf(xrow+mapOf("type" to "default,xcap")),xcap) }
    rejects { LocalPbApnRules.internetId(listOf(xrow),3) }
    expect(LocalPbRules.validMode(LocalPbRules.XCAP_V8))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.XCAP_V8))
    rejects { LocalPbRules.decode(profile(config,version=8)) }
    expect(LocalPbRules.decode(fixture).flags["carrier_vt_available_bool"]==true)
    expect(!ProfileOverviewRules.configVerified(overview+mapOf("carrier_vt_available_bool" to false)))
    expect(!ProfileOverviewRules.configVerified(overview-"carrier_vt_available_bool"))
    expect(LocalPbRules.validMode(LocalPbRules.VT_V9))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.VT_V9))
    rejects { LocalPbRules.decode(profile(config,version=9)) }
    expect(LocalPbRules.validMode(LocalPbRules.NO_VT_V10))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.NO_VT_V10))
    rejects { LocalPbRules.decode(profile(config,version=10)) }
    expect(LocalPbRules.decode(fixture).strings==LocalPbRules.expectedStrings)
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.XCAP_SERVER_KEY))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.XCAP_SERVER_KEY to "other.example")))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.XCAP_SERVER_KEY to true)))
    val badServer=message(2,text(1,LocalPbRules.XCAP_SERVER_KEY)+text(2,"other.example"))+apnEntry
    rejects { LocalPbRules.decode(message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config+ussd+badServer)) }
    rejects { LocalPbRules.decode(profile(config+server)) }
    expect(LocalPbRules.validMode(LocalPbRules.SERVER_V11))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.SERVER_V11))
    rejects { LocalPbRules.decode(profile(config,version=11)) }
    expect(LocalPbRules.decode(fixture).strings[LocalPbRules.XCAP_APN_KEY]=="xcap")
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.XCAP_APN_KEY))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.XCAP_APN_KEY to "default")))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.XCAP_APN_KEY to true)))
    fun withStrings(entries:ByteArray)=message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config+ussd+entries+bsfEntry+bsfPort+voicemail)
    rejects { LocalPbRules.decode(withStrings(serverEntry)) }
    rejects { LocalPbRules.decode(withStrings(apnEntry)) }
    rejects { LocalPbRules.decode(withStrings(serverEntry+serverEntry)) }
    rejects { LocalPbRules.decode(withStrings(serverEntry+message(2,text(1,LocalPbRules.XCAP_APN_KEY)+text(2,"default")))) }
    rejects { LocalPbRules.decode(withStrings(serverEntry+message(2,text(1,LocalPbRules.XCAP_APN_KEY)+integer(5,1)))) }
    expect(LocalPbRules.decode(withStrings(apnEntry+serverEntry)).strings==LocalPbRules.expectedStrings)
    expect(LocalPbRules.validMode(LocalPbRules.XCAP_APN_V12))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.XCAP_APN_V12))
    rejects { LocalPbRules.decode(profile(config,version=12)) }
    expect(LocalPbRules.decode(fixture).strings[LocalPbRules.BSF_KEY]==LocalPbRules.BSF_SERVER)
    expect(LocalPbRules.decode(fixture).integers[LocalPbRules.BSF_PORT_KEY]==8080)
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.BSF_KEY))
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.BSF_PORT_KEY))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.BSF_PORT_KEY to 80)))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.BSF_PORT_KEY to "8080")))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.BSF_KEY to "other.example")))
    fun rawConfig(extra:ByteArray)=message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config+ussd+serverEntry+apnEntry+extra)
    rejects { LocalPbRules.decode(rawConfig(bsfEntry)) }
    rejects { LocalPbRules.decode(rawConfig(bsfPort)) }
    rejects { LocalPbRules.decode(rawConfig(bsfEntry+message(2,text(1,LocalPbRules.BSF_PORT_KEY)+integer(3,80)))) }
    rejects { LocalPbRules.decode(rawConfig(bsfEntry+message(2,text(1,LocalPbRules.BSF_PORT_KEY)+text(2,"8080")))) }
    rejects { LocalPbRules.decode(rawConfig(message(2,text(1,LocalPbRules.BSF_KEY)+text(2,"other.example"))+bsfPort)) }
    rejects { LocalPbRules.decode(rawConfig(bsfEntry+bsfEntry)) }
    expect(LocalPbRules.validMode(LocalPbRules.BSF_V13))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.BSF_V13))
    rejects { LocalPbRules.decode(profile(config,version=13)) }
    expect(LocalPbRules.decode(fixture).flags["vendor.ims.carrier_video_over_lte_bool"]==true)
    expect(!ProfileOverviewRules.configVerified(overview-"vendor.ims.carrier_video_over_lte_bool"))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf("vendor.ims.carrier_video_over_lte_bool" to false)))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf("vendor.ims.carrier_video_over_lte_bool" to 1)))
    expect(LocalPbRules.validMode(LocalPbRules.VT_PROBE_V14))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.VT_PROBE_V14))
    rejects { LocalPbRules.decode(profile(config,version=14)) }
    expect(LocalPbRules.decode(fixture).strings[LocalPbRules.VOICEMAIL_KEY]=="+375296000200")
    expect(!ProfileOverviewRules.configVerified(overview-LocalPbRules.VOICEMAIL_KEY))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.VOICEMAIL_KEY to "441")))
    expect(!ProfileOverviewRules.configVerified(overview+mapOf(LocalPbRules.VOICEMAIL_KEY to true)))
    val legacyXcap=xrow+mapOf("user_visible" to "1")
    expect(LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap),3)==listOf(400L))
    expect(LocalPbApnRules.xcapVisibilityUpdates(listOf(xrow),3).isEmpty())
    expect(LocalPbApnRules.xcapVisibilityUpdates(created,3).isEmpty())
    expect(LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap),2).isEmpty())
    rejects { LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("numeric" to "26003")),3) }
    rejects { LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("protocol" to "IP")),3) }
    rejects { LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("proxy" to "10.0.0.1")),3) }
    rejects { LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("type" to "default,xcap")),3) }
    rejects { LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("user_visible" to "2")),3) }
    expect(LocalPbApnRules.xcapVisibilityUpdates(listOf(legacyXcap+mapOf("type" to "dun")),3).isEmpty())
    rejects { LocalPbRules.decode(profile(config+voicemail)) }
    val wrongVm=message(2,text(1,LocalPbRules.VOICEMAIL_KEY)+text(2,"+375296000210"))
    rejects { LocalPbRules.decode(message(1,"a1_by".toByteArray())+integer(2,16)+apns+message(4,config+ussd+serverEntry+apnEntry+bsfEntry+bsfPort+wrongVm)) }
    expect(LocalPbRules.validMode(LocalPbRules.VOICEMAIL_V15))
    expect(!LocalPbRules.canSubmitMode(LocalPbRules.VOICEMAIL_V15))
    rejects { LocalPbRules.decode(profile(config,version=15)) }
    count+=apnCleanupChecks()
    expect(BundledProfiles.available.map { it.id }==listOf("a1_by","mts_by","mts_ru","beeline_ru","megafon_ru","t2_ru"))
    expect(BundledProfiles.find("257","01")?.id=="a1_by")
    expect(BundledProfiles.canApply(local) && BundledProfiles.canApply(clear))
    expect(BundledProfiles.canApply(local.copy(slot=1,subId=2,embedded=true)))
    expect(!BundledProfiles.canApply(null))
    for(retired in listOf(plain,plain.copy(reset=true),plain.copy(mcc="222",mnc="88"),
        plain.copy(sourceMcc="260",sourceMnc="03",mcc="260",mnc="03"),
        plain.copy(sourceMnc="02",mnc="02"),plain.copy(sourceMcc="250",sourceMnc="01",mcc="250",mnc="01"),
        local.copy(configMode=LocalPbRules.VOICEMAIL_V15),local.copy(persistConfig=true))) {
        expect(retired.valid()) // Existing archives must still decode.
        expect(!BundledProfiles.canApply(retired))
    }
    expect(!BundledProfiles.canApply(local.copy(subId=-1)))
    expect(!BundledProfiles.canApply(local.copy(slot=2)))
    count+=mtsByChecks()
    count+=mtsRuChecks()
    return count
}

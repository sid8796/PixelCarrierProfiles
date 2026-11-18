package com.example.simregionswitcher

/** Restricted protobuf reader for the pinned carrier profiles with exact APNs. */
object LocalPbRules {
    const val APPLY = "a1_by_v16"
    const val VOICEMAIL_V15 = "a1_by_v15"
    const val VT_PROBE_V14 = "a1_by_v14"
    const val BSF_V13 = "a1_by_v13"
    const val XCAP_APN_V12 = "a1_by_v12"
    const val SERVER_V11 = "a1_by_v11"
    const val NO_VT_V10 = "a1_by_v10"
    const val VT_V9 = "a1_by_v9"
    const val XCAP_V8 = "a1_by_v8"
    const val ICON_V7 = "a1_by_v7"
    const val USSD_V6 = "a1_by_v6"
    const val APN_V5 = "a1_by_v5"
    const val PREVIOUS_APPLY = "a1_by_v2"
    const val LEGACY_APPLY = "a1_by_v1"
    const val CLEAR = "a1_by_clear_v1"
    val keys = listOf("carrier_volte_available_bool", "carrier_wfc_ims_available_bool", "carrier_supports_ss_over_ut_bool", "show_4g_for_lte_data_icon_bool", "hide_lte_plus_data_icon_bool", "carrier_vt_available_bool", "vendor.ims.carrier_video_over_lte_bool", "carrier_default_wfc_ims_enabled_bool")
    val expectedFlags get()=keys.associateWith { it!="hide_lte_plus_data_icon_bool" && it!="carrier_default_wfc_ims_enabled_bool" }
    const val USSD_KEY="carrier_ussd_method_int"
    const val XCAP_SERVER_KEY="imsss.ut_as_server_fqdn_string"
    const val XCAP_SERVER="xcap.ims.mnc001.mcc257.pub.3gppnetwork.org"
    const val XCAP_APN_KEY="xcap_apn_name_string"
    const val XCAP_APN="xcap"
    const val BSF_KEY="bsf.bsf_server_fqdn_string"
    const val BSF_SERVER="bsf.ims.mnc001.mcc257.pub.3gppnetwork.org"
    const val BSF_PORT_KEY="bsf.bsf_server_port_int"
    val expectedIntegers get()=mapOf(USSD_KEY to 1, BSF_PORT_KEY to 8080)
    const val VOICEMAIL_KEY="default_vm_number_string"
    const val VOICEMAIL_NUMBER="+375296000200"
    val expectedStrings get()=mapOf(XCAP_SERVER_KEY to XCAP_SERVER, XCAP_APN_KEY to XCAP_APN, BSF_KEY to BSF_SERVER, VOICEMAIL_KEY to VOICEMAIL_NUMBER)
    val allKeys get()=keys+expectedIntegers.keys+expectedStrings.keys
    data class Profile(val name: String, val version: Long, val flags: Map<String, Boolean>,val integers:Map<String,Int>,val strings:Map<String,String>,val arrays:Map<String,List<Int>> = emptyMap())
    private data class Field(val tag: Int, val number: Long? = null, val data: ByteArray? = null)
    private class Cursor(val bytes: ByteArray) {
        var index=0
        fun integer(): Long {
            var value=0L
            for(shift in 0..56 step 7) {
                require(index<bytes.size) { "TRUNCATED_VARINT" }
                val b=bytes[index++].toInt() and 255
                value=value or ((b and 127).toLong() shl shift)
                if(b and 128==0) return value
            }
            error("VARINT_OVERFLOW")
        }
        fun fields(): List<Field> {
            val result=mutableListOf<Field>()
            while(index<bytes.size) {
                require(result.size<16)
                val raw=integer();require(raw>0 && raw<=Int.MAX_VALUE)
                val tag=(raw shr 3).toInt();require(tag>0)
                result+=when((raw and 7).toInt()) {
                    0 -> Field(tag,number=integer())
                    2 -> { val count=integer();require(count<=bytes.size-index);val end=index+count.toInt();Field(tag,data=bytes.copyOfRange(index,end)).also { index=end } }
                    else -> error("UNSUPPORTED_WIRE_TYPE")
                }
            }
            return result
        }
    }
    fun decode(bytes: ByteArray): Profile {
        require(bytes.size in 1..4096)
        val outer=Cursor(bytes).fields();require(outer.size==4 && outer.map { it.tag }.toSet()==setOf(1,2,3,4))
        val name=outer.single { it.tag==1 }.data?.decodeToString(throwOnInvalidSequence=true) ?: error("NAME_TYPE")
        val version=outer.single { it.tag==2 }.number ?: error("VERSION_TYPE")
        val mts=name=="mts_by" && version==1L
        val ru=name=="mts_ru" && version==2L
        val beeline=name=="beeline_ru" && version==1L
        val megafon=name=="megafon_ru" && version==2L
        val t2=name=="t2_ru" && version==1L
        require(t2 || megafon || beeline || ru || mts || name=="a1_by" && version==16L)
        val expectedFlags=if(t2) T2RuRules.expectedFlags else if(megafon) MegafonRuRules.expectedFlags else if(beeline) BeelineRuRules.expectedFlags else if(ru) MtsRuRules.expectedFlags else if(mts) MtsByRules.expectedFlags else this.expectedFlags
        val keys=expectedFlags.keys.toList()
        val expectedIntegers=if(t2) T2RuRules.expectedIntegers else if(megafon) MegafonRuRules.expectedIntegers else if(beeline) BeelineRuRules.expectedIntegers else if(ru) MtsRuRules.expectedIntegers else if(mts) MtsByRules.expectedIntegers else this.expectedIntegers
        val expectedStrings=if(t2) T2RuRules.expectedStrings else if(megafon) MegafonRuRules.expectedStrings else if(beeline) BeelineRuRules.expectedStrings else if(ru) emptyMap() else if(mts) MtsByRules.expectedStrings else this.expectedStrings
        val expectedArrays=if(megafon) MegafonRuRules.expectedArrays else emptyMap()
        val allKeys=keys+expectedIntegers.keys+expectedStrings.keys+expectedArrays.keys
        val apns=Cursor(outer.single { it.tag==3 }.data ?: error("APN_TYPE")).fields()
        require(apns.size==(if(ru) 3 else 4) && apns.all { it.tag==2 && it.data!=null })
        val expected=if(t2) listOf(listOf(Field(1,data="T2 RU Internet".toByteArray()),Field(2,data="internet".toByteArray()),Field(3,number=1),Field(14,number=2),Field(15,number=2)),
            listOf(Field(1,data="T2 RU MMS".toByteArray()),Field(2,data="mms".toByteArray()),Field(3,number=2),Field(11,data="http://mms".toByteArray()),Field(12,data="193.12.40.65".toByteArray()),Field(13,data="8080".toByteArray()),Field(14,number=0),Field(15,number=0)),
            listOf(Field(1,data="T2 RU IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(14,number=2),Field(15,number=2),Field(23,number=0)),
            listOf(Field(1,data="T2 RU XCAP".toByteArray()),Field(2,data="hos".toByteArray()),Field(3,number=11),Field(14,number=2),Field(15,number=2),Field(23,number=0))) else if(megafon) listOf(listOf(Field(1,data="Megafon RU Internet".toByteArray()),Field(2,data="internet".toByteArray()),Field(3,number=1),Field(3,number=3),Field(8,data="gdata".toByteArray()),Field(9,data="gdata".toByteArray()),Field(10,number=1),Field(14,number=2),Field(15,number=2)),
            listOf(Field(1,data="Megafon RU MMS".toByteArray()),Field(2,data="mms".toByteArray()),Field(3,number=2),Field(8,data="mms".toByteArray()),Field(9,data="mms".toByteArray()),Field(10,number=1),Field(11,data="http://mmsc:8002".toByteArray()),Field(12,data="10.10.10.10".toByteArray()),Field(13,data="8080".toByteArray()),Field(14,number=0),Field(15,number=0)),
            listOf(Field(1,data="Megafon RU IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(14,number=2),Field(15,number=2),Field(23,number=0)),
            listOf(Field(1,data="Megafon RU XCAP".toByteArray()),Field(2,data="hos".toByteArray()),Field(3,number=11),Field(14,number=2),Field(15,number=2),Field(23,number=0))) else if(beeline) listOf(listOf(Field(1,data="Beeline RU Internet".toByteArray()),Field(2,data="internet.beeline.ru".toByteArray()),Field(3,number=1),Field(3,number=3),Field(8,data="beeline".toByteArray()),Field(9,data="beeline".toByteArray()),Field(10,number=1),Field(14,number=2),Field(15,number=2)),
            listOf(Field(1,data="Beeline RU MMS".toByteArray()),Field(2,data="mms.beeline.ru".toByteArray()),Field(3,number=2),Field(8,data="beeline".toByteArray()),Field(9,data="beeline".toByteArray()),Field(10,number=1),Field(11,data="http://mms/".toByteArray()),Field(12,data="192.168.94.23".toByteArray()),Field(13,data="8080".toByteArray()),Field(14,number=0),Field(15,number=0)),
            listOf(Field(1,data="Beeline RU IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(14,number=0),Field(15,number=0),Field(23,number=0)),
            listOf(Field(1,data="Beeline RU XCAP".toByteArray()),Field(2,data="xcap.beeline.ru".toByteArray()),Field(3,number=11),Field(14,number=2),Field(15,number=2),Field(23,number=0))) else if(ru) listOf(
            listOf(Field(1,data="MTS RU Internet".toByteArray()),Field(2,data="internet.mts.ru".toByteArray()),Field(3,number=1),Field(3,number=3),Field(8,data="mts".toByteArray()),Field(9,data="mts".toByteArray()),Field(10,number=1),Field(14,number=2),Field(15,number=2)),
            listOf(Field(1,data="MTS RU MMS".toByteArray()),Field(2,data="mms.mts.ru".toByteArray()),Field(3,number=2),Field(8,data="mts".toByteArray()),Field(9,data="mts".toByteArray()),Field(10,number=1),Field(11,data="http://mmsc".toByteArray()),Field(12,data="192.168.192.192".toByteArray()),Field(13,data="8080".toByteArray()),Field(14,number=0),Field(15,number=0)),
            listOf(Field(1,data="MTS RU IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(14,number=2),Field(15,number=2),Field(23,number=0))) else if(mts) listOf(
            listOf(Field(1,data="MTS BY Internet".toByteArray()),Field(2,data="mts".toByteArray()),Field(3,number=1),Field(3,number=3),Field(8,data="mts".toByteArray()),Field(9,data="mts".toByteArray()),Field(10,number=1)),
            listOf(Field(1,data="MTS BY MMS".toByteArray()),Field(2,data="mts".toByteArray()),Field(3,number=2),Field(8,data="mts".toByteArray()),Field(9,data="mts".toByteArray()),Field(10,number=1),Field(11,data="http://mmsc".toByteArray()),Field(12,data="192.168.192.168".toByteArray()),Field(13,data="8080".toByteArray())),
            listOf(Field(1,data="MTS BY IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(4,data="14|18|20".toByteArray()),Field(14,number=2),Field(15,number=2),Field(23,number=0)),
            listOf(Field(1,data="MTS BY XCAP".toByteArray()),Field(2,data="xcap".toByteArray()),Field(3,number=11),Field(14,number=2),Field(15,number=2))) else listOf(
            listOf(Field(1,data="A1 BY Internet".toByteArray()),Field(2,data="internet".toByteArray()),Field(3,number=1),Field(3,number=3),Field(10,number=1),Field(14,number=2),Field(15,number=2),Field(24,number=0)),
            listOf(Field(1,data="A1 BY MMS".toByteArray()),Field(2,data="mms".toByteArray()),Field(3,number=2),Field(10,number=1),Field(11,data="http://mmsc".toByteArray()),Field(14,number=2),Field(15,number=2),Field(24,number=0)),
            listOf(Field(1,data="A1 BY IMS".toByteArray()),Field(2,data="ims".toByteArray()),Field(3,number=7),Field(4,data="14|18|20".toByteArray()),Field(14,number=2),Field(15,number=2),Field(23,number=0)),
            listOf(Field(1,data="A1 BY XCAP".toByteArray()),Field(2,data="xcap".toByteArray()),Field(3,number=11),Field(14,number=2),Field(15,number=2),Field(23,number=0)))
        apns.zip(expected).forEach { (apn,want) ->
            val actual=Cursor(requireNotNull(apn.data)).fields()
            require(actual.size==want.size && actual.zip(want).all { (a,b) -> a.tag==b.tag && a.number==b.number && (a.data?.toList()==b.data?.toList()) }) { "APN_NOT_WHITELISTED" }
        }
        val entries=Cursor(outer.single { it.tag==4 }.data ?: error("CONFIG_TYPE")).fields()
        require(entries.size==allKeys.size && entries.all { it.tag==2 && it.data!=null })
        val flags=linkedMapOf<String,Boolean>()
        val integers=linkedMapOf<String,Int>()
        val strings=linkedMapOf<String,String>()
        val arrays=linkedMapOf<String,List<Int>>()
        for(entry in entries) {
            val values=Cursor(requireNotNull(entry.data)).fields()
            require(values.size==2 && values.count { it.tag==1 }==1)
            val key=values.single { it.tag==1 }.data?.decodeToString(throwOnInvalidSequence=true) ?: error("KEY_TYPE")
            if(key in expectedArrays) {
                require(values.map { it.tag }.toSet()==setOf(1,7) && !arrays.containsKey(key))
                val fields=Cursor(values.single { it.tag==7 }.data ?: error("ARRAY_TYPE")).fields()
                require(fields.isNotEmpty() && fields.size<=8 && fields.all { it.tag==1 && it.number in 0L..Int.MAX_VALUE.toLong() })
                val value=fields.map { requireNotNull(it.number).toInt() }
                require(value==expectedArrays.getValue(key)) { "ARRAY_NOT_APPROVED" }
                arrays[key]=value
            } else if(key in expectedStrings) {
                require(values.map { it.tag }.toSet()==setOf(1,2) && !strings.containsKey(key))
                val value=values.single { it.tag==2 }.data?.decodeToString(throwOnInvalidSequence=true) ?: error("STRING_TYPE")
                require(value==expectedStrings.getValue(key)) { "STRING_NOT_APPROVED" }
                strings[key]=value
            } else if(key in expectedIntegers) {
                require(values.map { it.tag }.toSet()==setOf(1,3) && !integers.containsKey(key))
                val value=values.single { it.tag==3 }.number ?: error("INT_TYPE")
                require(value==expectedIntegers.getValue(key).toLong()) { "INT_VALUE_NOT_APPROVED" }
                integers[key]=value.toInt()
            } else {
                require(values.map { it.tag }.toSet()==setOf(1,5))
                val value=values.single { it.tag==5 }.number ?: error("BOOL_TYPE")
                require(key in keys && !flags.containsKey(key) && value in 0L..1L)
                flags[key]=value==1L
            }
        }
        require(flags.keys==keys.toSet())
        require(integers==expectedIntegers)
        require(strings==expectedStrings)
        require(arrays==expectedArrays)
        return Profile(name,version,flags.toMap(),integers.toMap(),strings.toMap(),arrays.toMap())
    }
    fun canSubmitMode(mode: String)=mode in setOf("",APPLY,CLEAR,T2RuRules.APPLY,T2RuRules.CLEAR,MegafonRuRules.APPLY,MegafonRuRules.CLEAR,BeelineRuRules.APPLY,BeelineRuRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR,MtsRuRules.APPLY,MtsRuRules.CLEAR)
    fun validMode(mode: String)=mode in setOf("",APPLY,CLEAR,T2RuRules.APPLY,T2RuRules.CLEAR,MegafonRuRules.APPLY,MegafonRuRules.CLEAR,MegafonRuRules.PREVIOUS_APPLY,BeelineRuRules.APPLY,BeelineRuRules.CLEAR,MtsByRules.APPLY,MtsByRules.CLEAR,MtsRuRules.APPLY,MtsRuRules.CLEAR,MtsRuRules.PREVIOUS_APPLY,LEGACY_APPLY,PREVIOUS_APPLY,APN_V5,USSD_V6,ICON_V7,XCAP_V8,VT_V9,NO_VT_V10,SERVER_V11,XCAP_APN_V12,BSF_V13,VT_PROBE_V14,VOICEMAIL_V15)
    fun validTarget(mode: String,reset: Boolean,sourceMcc: String,sourceMnc: String,mcc: String,mnc: String)=
        validMode(mode) && (mode.isEmpty() || !reset && sourceMcc==(if(mode in setOf(T2RuRules.APPLY,T2RuRules.CLEAR,MegafonRuRules.APPLY,MegafonRuRules.CLEAR,MegafonRuRules.PREVIOUS_APPLY,BeelineRuRules.APPLY,BeelineRuRules.CLEAR,MtsRuRules.APPLY,MtsRuRules.CLEAR,MtsRuRules.PREVIOUS_APPLY)) "250" else "257") && sourceMnc==(if(mode in setOf(T2RuRules.APPLY,T2RuRules.CLEAR)) "20" else if(mode in setOf(BeelineRuRules.APPLY,BeelineRuRules.CLEAR)) "99" else if(mode in setOf(MtsByRules.APPLY,MtsByRules.CLEAR,MegafonRuRules.APPLY,MegafonRuRules.CLEAR,MegafonRuRules.PREVIOUS_APPLY)) "02" else "01") && mcc==sourceMcc && mnc==sourceMnc)
}

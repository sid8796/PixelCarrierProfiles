package com.example.simregionswitcher

/** Exact MTS rows only. No deletion or migration of unrelated/provider APNs. */
object MtsByApnRules {
    val definitions=listOf(
        LocalPbApnRules.Definition("mts","default,supl",1,protocol="IP",user="mts",password="mts"),
        LocalPbApnRules.Definition("mts","mms",1,"http://mmsc",protocol="IP",user="mts",password="mts",mmsproxy="192.168.192.168",mmsport="8080"),
        LocalPbApnRules.Definition("ims","ims",-1,bearer=(1 shl 13) or (1 shl 17) or (1 shl 19),visible=0),
        LocalPbApnRules.Definition("xcap","xcap",-1))
    fun targeted(rows:List<Map<String,String>>,subId:Int,d:LocalPbApnRules.Definition):List<Map<String,String>> {
        require(subId>=0 && d in definitions)
        require(rows.all { it["numeric"]=="25702" }) { "APN_REPLY_INVALID" }
        return rows.filter { it["sub_id"]==subId.toString() && it["apn"]==d.apn }
    }
    fun missing(rows:List<Map<String,String>>,subId:Int)=definitions.filter { !LocalPbApnRules.exists(targeted(rows,subId,it),it) }
    fun internetId(rows:List<Map<String,String>>,subId:Int,preferred:Long?):Long {
        check(missing(rows,subId).isEmpty()) { "APN_READBACK_FAILED" }
        val d=definitions.first()
        val ids=targeted(rows,subId,d).filter { LocalPbApnRules.exists(listOf(it),d) }.map { it.getValue("_id").toLong() }
        check(ids.isNotEmpty()) { "APN_NOT_UNIQUE" }
        return preferred?.takeIf { it in ids } ?: ids.min()
    }
}

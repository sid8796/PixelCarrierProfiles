package com.example.simregionswitcher

/** Exact Beeline Russia rows only. No deletion or migration of unrelated/provider APNs. */
object BeelineRuApnRules {
    val definitions=listOf(
        LocalPbApnRules.Definition("internet.beeline.ru","default,supl",1,user="beeline",password="beeline"),
        LocalPbApnRules.Definition("mms.beeline.ru","mms",1,"http://mms/",protocol="IP",user="beeline",password="beeline",mmsproxy="192.168.94.23",mmsport="8080"),
        LocalPbApnRules.Definition("ims","ims",-1,protocol="IP",visible=0),
        LocalPbApnRules.Definition("xcap.beeline.ru","xcap",-1,visible=0))
    fun targeted(rows:List<Map<String,String>>,subId:Int,d:LocalPbApnRules.Definition):List<Map<String,String>> {
        require(subId>=0 && d in definitions)
        require(rows.all { it["numeric"]=="25099" }) { "APN_REPLY_INVALID" }
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

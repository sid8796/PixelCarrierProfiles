package com.example.simregionswitcher

/** Parses only bounded content-tool rows; provider errors can never be treated as an empty table. */
object LocalPbApnRules {
    // AOSP Telephony.Carriers: DPC=0, other sources=1. Never infer ownership from truthiness.
    const val OWNED_BY_OTHERS="1"
    val columns=listOf("_id","apn","numeric","sub_id","type","authtype","mmsc","protocol","roaming_protocol","bearer_bitmask","user_visible","carrier_enabled","proxy","port","user","password","server","mmsproxy","mmsport","user_editable","name","mvno_type","mvno_match_data","apn_set_id","network_type_bitmask","carrier_id","edited","owned_by","bearer","profile_id","modem_cognitive","max_conns","wait_time","max_conns_time","mtu","mtu_v4","mtu_v6","skip_464xlat","lingering_network_type_bitmask","always_on","infrastructure_bitmask","esim_bootstrap_provisioning")
    data class Definition(val apn:String,val type:String,val auth:Int,val mmsc:String="",val bearer:Int=0,val visible:Int=1,val editable:Int=1,val protocol:String="IPV4V6",val user:String="",val password:String="",val mmsproxy:String="",val mmsport:String="")
    val definitions=listOf(Definition("internet","default,supl",1,editable=0),Definition("mms","mms",1,"http://mmsc",editable=0),Definition("ims","ims",-1,bearer=(1 shl 13) or (1 shl 17) or (1 shl 19),visible=0),Definition("xcap","xcap",-1,visible=0))
    fun escapedBinding(binding:String):String {
        val parts=binding.split(':',limit=3)
        require(parts.size==3 && parts[0].matches(Regex("[a-z_]+")) && parts[1] in setOf("s","i"))
        val value=parts[2].replace("\\","\\\\").replace(":","\\:")
        return parts[0]+":"+parts[1]+":"+value
    }
    fun commandError(output:String):Boolean = output.contains("[ERROR]") || output.trimStart().startsWith("usage:") || output.contains("Exception") || output.contains("Error while accessing provider")
    const val selection="numeric=25701"
    fun targeted(rows:List<Map<String,String>>,subId:Int,apn:String):List<Map<String,String>> {
        require(subId>=0 && definitions.any { it.apn==apn })
        require(rows.all { it["numeric"]=="25701" }) { "APN_REPLY_INVALID" }
        return rows.filter { it["sub_id"]==subId.toString() && it["apn"]==apn }
    }
    fun parse(output:String):List<Map<String,String>> {
        require(output.length<=100000) { "APN_REPLY_TOO_LARGE" }
        if(output.trim()=="No result found.") return emptyList()
        require(output.isNotBlank()) { "APN_REPLY_INVALID" }
        val pattern=Regex("(?:^|, )("+columns.joinToString("|")+")=")
        val rows=output.lineSequence().filter { it.isNotBlank() }.map { line ->
            require(line.matches(Regex("Row: [0-9]+ .*"))) { "APN_REPLY_INVALID" }
            val body=line.substringAfter(' ').substringAfter(' ')
            val tags=pattern.findAll(body).toList()
            require(tags.map { it.groupValues[1] }==columns) { "APN_REPLY_INVALID" }
            val values=tags.mapIndexed { i,m -> body.substring(m.range.last+1,if(i+1<tags.size) tags[i+1].range.first else body.length).let { if(it in setOf("NULL","null")) "" else it } }
            require(values.all { it.length<=500 && it.none { c -> c.isISOControl() } }) { "APN_REPLY_INVALID" }
            columns.zip(values).toMap().also { require(it.getValue("_id").toLong()>=0) }
        }.toList()
        require(rows.map { it.getValue("_id") }.distinct().size==rows.size) { "APN_REPLY_INVALID" }
        return rows
    }
    /** Only migrate the visibility of an otherwise exact legacy XCAP row on this SIM. */
    fun xcapVisibilityUpdates(rows:List<Map<String,String>>,subId:Int):List<Long> {
        val definition=definitions.single { it.apn=="xcap" }
        return targeted(rows,subId,"xcap").mapNotNull { row ->
            if(row["user_visible"]=="1") {
                if(exists(listOf(row),definition.copy(visible=1))) {
                    check(manageable(row,subId)) { "EXISTING_APN_CONFLICT" };row.getValue("_id").toLong()
                } else null
            } else {
                exists(listOf(row),definition)
                null
            }
        }
    }
    fun missing(rows:List<Map<String,String>>,subId:Int):List<Definition> = definitions.filter { d -> !exists(targeted(rows,subId,d.apn),d) }
    /** Lock otherwise exact Internet/MMS rows; never overwrite connectivity fields. */
    fun readOnlyUpdates(rows:List<Map<String,String>>,subId:Int):List<Long> = definitions.filter { it.editable==0 }.flatMap { d ->
        targeted(rows,subId,d.apn).mapNotNull { row ->
            val editable=row["user_editable"]
            if(editable in setOf("0","1")) {
                if(exists(listOf(row),d.copy(editable=editable!!.toInt()))) {
                    check(manageable(row,subId)) { "EXISTING_APN_CONFLICT" }
                    if(editable=="1") row.getValue("_id").toLong() else null
                } else null
            } else { exists(listOf(row),d); null }
        }
    }
    fun normalizeReadOnly(rows:List<Map<String,String>>,subId:Int):List<Map<String,String>> {
        val ids=readOnlyUpdates(rows,subId).toSet()
        return rows.map { if(it.getValue("_id").toLong() in ids) it+mapOf("user_editable" to "0") else it }
    }
    fun manageable(row:Map<String,String>,subId:Int)=subId>=0 && row["numeric"]=="25701" && row["sub_id"]==subId.toString() &&
        row["owned_by"]==OWNED_BY_OTHERS && row["mvno_type"].isNullOrEmpty() && row["mvno_match_data"].isNullOrEmpty() && row["carrier_id"] in setOf("-1","568")
    fun exactRows(rows:List<Map<String,String>>,subId:Int,d:Definition)=targeted(rows,subId,d.apn).filter { exists(listOf(it),d) }
    fun internetId(rows:List<Map<String,String>>,subId:Int,preferred:Long?=null):Long {
        val candidates=exactRows(rows,subId,definitions.first())
        check(candidates.isNotEmpty()) { "APN_NOT_UNIQUE" }
        val ids=candidates.map { it.getValue("_id").toLong() }
        return preferred?.takeIf { it in ids } ?: ids.min()
    }
    /** Delete only an unchanged row, including its SIM and all inspected metadata. */
    fun rowSelection(row:Map<String,String>):String {
        require(row.keys==columns.toSet() && row.getValue("_id").toLong()>=0)
        return columns.joinToString(" AND ") { key ->
            val value=row.getValue(key)
            require(value.length<=500 && value.none { it.isISOControl() })
            if(value.isEmpty()) "($key IS NULL OR $key='')" else "$key='${value.replace("'","''")}'"
        }
    }
    fun visibleIds(output:String):Set<Long> {
        require(output.length<=100000) { "APN_REPLY_TOO_LARGE" }
        if(output.trim()=="No result found.") return emptySet()
        require(output.isNotBlank()) { "APN_REPLY_INVALID" }
        val ids=output.lineSequence().filter { it.isNotBlank() }.map { line ->
            val match=Regex("Row: [0-9]+ _id=([0-9]+)").matchEntire(line) ?: error("APN_REPLY_INVALID")
            match.groupValues[1].toLong()
        }.toList()
        require(ids.distinct().size==ids.size) { "APN_REPLY_INVALID" }
        return ids.toSet()
    }
    fun deletionSelection(rows:List<Map<String,String>>):String {
        require(rows.isNotEmpty() && rows.size<=50 && rows.map { it.getValue("_id") }.distinct().size==rows.size)
        return rows.joinToString(" OR ") { "("+rowSelection(it)+")" }
    }
    fun preferredId(output:String):Long? {
        if(output.trim()=="No result found.") return null
        val match=Regex("Row: 0 _id=([0-9]+)").matchEntire(output.trim()) ?: error("APN_REPLY_INVALID")
        return match.groupValues[1].toLong()
    }
    fun exists(rows:List<Map<String,String>>,d:Definition):Boolean {
        var found=false
        for(row in rows) {
            require(row.getValue("apn")==d.apn) { "APN_REPLY_INVALID" }
            val types=row.getValue("type").split(',').map { it.trim() }.toSet()
            if(types.intersect(d.type.split(',').toSet()).isEmpty() && "*" !in types) continue
            val exact=types==d.type.split(',').toSet() && row["authtype"]==d.auth.toString() && row["mmsc"]==d.mmsc && row["protocol"]==d.protocol && row["roaming_protocol"]==d.protocol && row["bearer_bitmask"]==d.bearer.toString() && row["user_visible"]==d.visible.toString() && row["user_editable"]==d.editable.toString() && row["carrier_enabled"]=="1" && row["user"].orEmpty()==d.user && row["password"].orEmpty()==d.password && row["mmsproxy"].orEmpty()==d.mmsproxy && row["mmsport"].orEmpty()==d.mmsport && listOf("proxy","port","server").all { row[it].isNullOrEmpty() }
            check(exact) { "EXISTING_APN_CONFLICT" }
            found=true
        }
        return found
    }
}

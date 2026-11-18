package com.example.simregionswitcher

/** Fixed cleanup policy: A1 duplicates stay subscription-scoped; legacy Velcom also follows the selected SIM APN view. */
object LocalPbApnCleanup {
    fun candidates(rows:List<Map<String,String>>,subId:Int,preferred:Long,visibleIds:Set<Long> = emptySet()):List<Map<String,String>> {
        require(subId>=0 && rows.all { it["numeric"]=="25701" }) { "APN_REPLY_INVALID" }
        val normalized=LocalPbApnRules.normalizeReadOnly(rows,subId)
        check(LocalPbApnRules.missing(normalized,subId).isEmpty()) { "APN_READBACK_FAILED" }
        check(LocalPbApnRules.internetId(normalized,subId,preferred)==preferred) { "APN_SELECTION_FAILED" }
        val selected=rows.filter { LocalPbApnRules.manageable(it,subId) }
        val result=mutableListOf<Map<String,String>>()
        for(d in LocalPbApnRules.definitions.filter { it.apn in setOf("internet","mms") }) {
            val matchingIds=LocalPbApnRules.exactRows(normalized,subId,d).map { it.getValue("_id") }.toSet()
            val matching=selected.filter { it["_id"] in matchingIds }
            // Numeric and subId already pin A1; unknown (-1) and A1 (568) IDs may describe the same APN.
            // Different radio restrictions, APN sets or MVNO metadata are not duplicates.
            val groups=matching.groupBy { row -> row.filterKeys { it !in setOf("_id","name","edited","user_editable","carrier_id") } }
            for(group in groups.values) {
                val keeper=group.firstOrNull { it["_id"]==preferred.toString() } ?: group.minBy { it.getValue("_id").toLong() }
                result+=group.filter { it!==keeper }
            }
        }
        for(row in rows.filter { row ->
            // Old carrier records may retain a stale sub_id while Settings still displays them for A1.
            LocalPbApnRules.manageable(row+mapOf("sub_id" to subId.toString()),subId) &&
                (row["sub_id"]==subId.toString() || row.getValue("_id").toLong() in visibleIds)
        }) {
            val types=row.getValue("type").split(',').map { it.trim() }.toSet()
            val legacy=when(row["apn"]) {
                "web.velcom.by" -> types.isNotEmpty() && types.all { it in setOf("default","supl","hipri") }
                "mms.velcom.by" -> types==setOf("mms")
                else -> false
            }
            if(legacy && row["user_visible"]=="1") result+=row
        }
        return result.filter { it["_id"]!=preferred.toString() }.distinctBy { it["_id"] }.sortedBy { it.getValue("_id").toLong() }
    }
    private fun needsVisibleView(rows:List<Map<String,String>>,subId:Int)=rows.any {
        it["apn"] in setOf("web.velcom.by","mms.velcom.by") && it["sub_id"]!=subId.toString()
    }
    /** Read once on the no-op path; revalidate immediately before writes and verify their results. */
    fun run(subId:Int,read:()->List<Map<String,String>>,preferred:()->Long?,select:(Long)->Unit,delete:(Map<String,String>)->Unit,mapped:()->Unit,
            lock:(Map<String,String>)->Unit={ error("APN_READONLY_WRITE_REQUIRED") },visible:()->Set<Long> = {emptySet()},
            deleteBatch:((List<Map<String,String>>)->Unit)?=null):Boolean {
        mapped()
        val rows=read()
        val normalized=LocalPbApnRules.normalizeReadOnly(rows,subId)
        check(LocalPbApnRules.missing(normalized,subId).isEmpty()) { "APN_READBACK_FAILED" }
        val before=preferred()
        val id=LocalPbApnRules.internetId(normalized,subId,before)
        val changed=before!=id
        if(changed) { mapped();select(id);check(preferred()==id) { "APN_SELECTION_FAILED" } }
        var expected=if(changed) read() else rows
        fun visibleFor(snapshot:List<Map<String,String>>)=if(needsVisibleView(snapshot,subId)) visible() else emptySet()
        val planned=candidates(expected,subId,id,visibleFor(expected))
        var verifiedAfterWrite=false
        if(planned.isNotEmpty() && deleteBatch!=null) {
            mapped();check(preferred()==id) { "APN_SELECTION_FAILED" }
            val current=read()
            check(current==expected && candidates(current,subId,id,visibleFor(current))==planned) { "APN_CLEANUP_CHANGED" }
            check(planned.size<=50) { "APN_REPLY_TOO_LARGE" }
            deleteBatch(planned)
            val removed=planned.map { it.getValue("_id") }.toSet()
            expected=current.filter { it["_id"] !in removed }
            check(read()==expected) { "APN_READBACK_FAILED" }
            verifiedAfterWrite=true
        } else for(row in planned) {
            mapped();check(preferred()==id) { "APN_SELECTION_FAILED" }
            val current=read()
            check(current==expected && row in candidates(current,subId,id,visibleFor(current))) { "APN_CLEANUP_CHANGED" }
            delete(row)
            expected=current.filter { it["_id"]!=row["_id"] }
            check(read()==expected) { "APN_READBACK_FAILED" }
            verifiedAfterWrite=true
        }
        // Remove editable/read-only duplicate copies before locking the retained record.
        for(lockId in LocalPbApnRules.readOnlyUpdates(expected,subId)) {
            mapped();check(preferred()==id) { "APN_SELECTION_FAILED" }
            check(read()==expected) { "APN_CLEANUP_CHANGED" }
            val row=expected.single { it["_id"]==lockId.toString() }
            lock(row)
            val after=read()
            val updated=after.singleOrNull { it["_id"]==lockId.toString() } ?: error("APN_READBACK_FAILED")
            check(updated.filterKeys { it!="edited" }==(row+mapOf("user_editable" to "0")).filterKeys { it!="edited" }) { "APN_READBACK_FAILED" }
            check(after.filter { it["_id"]!=lockId.toString() }==expected.filter { it["_id"]!=lockId.toString() }) { "APN_CLEANUP_CHANGED" }
            expected=after;verifiedAfterWrite=true
        }
        if(!verifiedAfterWrite) check(read()==expected) { "APN_CLEANUP_CHANGED" }
        check(LocalPbApnRules.missing(expected,subId).isEmpty()) { "APN_READBACK_FAILED" }
        check(preferred()==id) { "APN_SELECTION_FAILED" }
        mapped();return changed
    }
}


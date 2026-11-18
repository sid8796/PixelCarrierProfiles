package com.example.simregionswitcher

import android.os.Bundle
import java.util.concurrent.TimeUnit

/** Runs only inside the shell-UID helper. Fixed argv, no shell interpolation or arbitrary URI/values. */
object LocalPbApns {
    private fun execute(args:List<String>,user:Int):String {
        val p=ProcessBuilder(listOf("/system/bin/content")+args+listOf("--user",user.toString())).redirectErrorStream(true).start()
        val output=StringBuilder()
        val failed=java.util.concurrent.atomic.AtomicBoolean(false)
        val reader=Thread {
            try { p.inputStream.bufferedReader().use { input ->
                val buffer=CharArray(2048)
                while(true) { val n=input.read(buffer);if(n<0) break
                    synchronized(output) {
                        if(output.length+n>100000) { failed.set(true);p.destroy();return@Thread }
                        output.append(buffer,0,n)
                    }
                }
            } } catch(_:Exception) { failed.set(true) }
        }.apply { isDaemon=true;start() }
        if(!p.waitFor(8,TimeUnit.SECONDS)) { p.destroyForcibly();reader.join(1000);error("APN_TIMEOUT") }
        reader.join(1000)
        check(!reader.isAlive && !failed.get()) { "APN_REPLY_INVALID" }
        val result=output.toString()
        if(result.contains("SecurityException") || result.contains("Permission Denial") || result.contains("Permission denied")) throw SecurityException("APN_ACCESS_DENIED")
        check(p.exitValue()==0 && !LocalPbApnRules.commandError(result)) { "APN_COMMAND_FAILED" }
        return result
    }
    fun install(plan:RegionPlan,user:Int,mapped:()->Unit):Bundle {
        if(plan.configMode in setOf(MtsByRules.APPLY,MtsRuRules.APPLY,BeelineRuRules.APPLY,MegafonRuRules.APPLY,T2RuRules.APPLY)) return installMts(plan,user,mapped)
        var mutated=false
        var currentApn="internet"
        return try {
            check(android.os.Process.myUid()==2000 && plan.configMode==LocalPbRules.APPLY && plan.valid())
            fun readRows():List<Map<String,String>> {
                mapped()
                return LocalPbApnRules.parse(execute(listOf("query","--uri","content://telephony/carriers","--projection",LocalPbApnRules.columns.joinToString(":"),"--where",LocalPbApnRules.selection),user))
            }
            // A single read validates all four definitions before any write.
            val initialRows=readRows()
            val visibilityIds=LocalPbApnRules.xcapVisibilityUpdates(initialRows,plan.subId)
            val readOnlyIds=LocalPbApnRules.readOnlyUpdates(initialRows,plan.subId)
            val normalizedRows=initialRows.map { row ->
                var updated=row
                if(row.getValue("_id").toLong() in visibilityIds) updated=updated+mapOf("user_visible" to "0")
                if(row.getValue("_id").toLong() in readOnlyIds) updated=updated+mapOf("user_editable" to "0")
                updated
            }
            val absent=LocalPbApnRules.missing(normalizedRows,plan.subId)
            for(id in visibilityIds) {
                currentApn="xcap"
                mapped()
                check(id in LocalPbApnRules.xcapVisibilityUpdates(readRows(),plan.subId)) { "APN_READBACK_FAILED" }
                mutated=true
                val where="_id=$id AND numeric='25701' AND sub_id=${plan.subId} AND apn='xcap' AND type='xcap' AND user_visible=1"
                execute(listOf("update","--uri","content://telephony/carriers","--bind","user_visible:i:0","--where",where),user)
            }
            for(d in absent) {
                currentApn=d.apn
                mapped()
                val name=when(d.apn) { "internet" -> "A1 BY Internet";"mms" -> "A1 BY MMS";"ims" -> "A1 BY IMS";else -> "A1 BY XCAP" }
                val binds=listOf("name:s:$name","apn:s:${d.apn}","numeric:s:25701","mcc:s:257","mnc:s:01","sub_id:i:${plan.subId}","type:s:${d.type}","authtype:i:${d.auth}","mmsc:s:${d.mmsc}","protocol:s:IPV4V6","roaming_protocol:s:IPV4V6","carrier_enabled:i:1","bearer_bitmask:i:${d.bearer}","user_visible:i:${d.visible}","user_editable:i:${d.editable}","edited:i:1","carrier_id:i:568")
                mutated=true
                execute(listOf("insert","--uri","content://telephony/carriers")+binds.flatMap { listOf("--bind",LocalPbApnRules.escapedBinding(it)) },user)
            }
            if(absent.isNotEmpty() || visibilityIds.isNotEmpty() || readOnlyIds.isNotEmpty()) {
                val remaining=LocalPbApnRules.missing(LocalPbApnRules.normalizeReadOnly(readRows(),plan.subId),plan.subId)
                if(remaining.isNotEmpty()) { currentApn=remaining.first().apn;error("APN_READBACK_FAILED") }
            }
            mapped()
            Bundle().apply { putBoolean("ok",true);putBoolean("mutated",mutated);putString("detail","native2:apn-shizuku:verified") }
        } catch(error:Exception) {
            val reason=when {
                error is SecurityException -> "APN_ACCESS_DENIED"
                error.message in setOf("EXISTING_APN_CONFLICT","APN_TIMEOUT","APN_REPLY_INVALID","APN_REPLY_TOO_LARGE","APN_READBACK_FAILED","APN_COMMAND_FAILED") -> error.message!!
                else -> "APN_CHECK_FAILED"
            }
            Bundle().apply { putBoolean("ok",false);putBoolean("mutated",mutated);putString("detail","native2:apn-shizuku:$reason:$currentApn") }
        }
    }
    fun selectInternet(plan:RegionPlan,user:Int,mapped:()->Unit):Boolean {
        if(plan.configMode in setOf(MtsByRules.APPLY,MtsRuRules.APPLY,BeelineRuRules.APPLY,MegafonRuRules.APPLY,T2RuRules.APPLY)) return selectMts(plan,user,mapped)
        check(android.os.Process.myUid()==2000 && plan.configMode==LocalPbRules.APPLY && plan.valid())
        fun readRows():List<Map<String,String>> {
            mapped()
            return LocalPbApnRules.parse(execute(listOf("query","--uri","content://telephony/carriers","--projection",LocalPbApnRules.columns.joinToString(":"),"--where",LocalPbApnRules.selection),user))
        }
        val uri="content://telephony/carriers/preferapn/subId/${plan.subId}"
        fun preferred():Long? {
            mapped()
            return LocalPbApnRules.preferredId(execute(listOf("query","--uri",uri,"--projection","_id"),user))
        }
        return LocalPbApnCleanup.run(plan.subId,::readRows,::preferred,select={ id ->
            execute(listOf("update","--uri",uri,"--bind","apn_id:l:$id"),user)
        },delete={ row ->
            execute(listOf("delete","--uri","content://telephony/carriers","--where",LocalPbApnRules.rowSelection(row)),user)
        },mapped=mapped,lock={ row ->
            execute(listOf("update","--uri","content://telephony/carriers","--bind","user_editable:i:0","--where",LocalPbApnRules.rowSelection(row)),user)
        },visible={
            mapped()
            LocalPbApnRules.visibleIds(execute(listOf("query","--uri","content://telephony/carriers/sim_apn_list/${plan.subId}","--projection","_id","--where",LocalPbApnRules.selection),user))
        },deleteBatch={ rows ->
            execute(listOf("delete","--uri","content://telephony/carriers","--where",LocalPbApnRules.deletionSelection(rows)),user)
        })
    }

    private fun otherMissing(plan:RegionPlan,rows:List<Map<String,String>>)=if(plan.configMode==T2RuRules.APPLY) T2RuApnRules.missing(rows,plan.subId) else if(plan.configMode==MegafonRuRules.APPLY) MegafonRuApnRules.missing(rows,plan.subId) else if(plan.configMode==BeelineRuRules.APPLY) BeelineRuApnRules.missing(rows,plan.subId) else if(plan.configMode==MtsRuRules.APPLY) MtsRuApnRules.missing(rows,plan.subId) else MtsByApnRules.missing(rows,plan.subId)
    private fun mtsRows(plan:RegionPlan,user:Int,mapped:()->Unit):List<Map<String,String>> {
        check(android.os.Process.myUid()==2000 && plan.configMode in setOf(MtsByRules.APPLY,MtsRuRules.APPLY,BeelineRuRules.APPLY,MegafonRuRules.APPLY,T2RuRules.APPLY) && BundledProfiles.canApply(plan))
        mapped()
        return LocalPbApnRules.parse(execute(listOf("query","--uri","content://telephony/carriers","--projection",LocalPbApnRules.columns.joinToString(":"),"--where","numeric=${plan.sourceMcc}${plan.sourceMnc}"),user))
    }
    private fun installMts(plan:RegionPlan,user:Int,mapped:()->Unit):Bundle {
        var mutated=false
        return try {
            val absent=otherMissing(plan,mtsRows(plan,user,mapped))
            for(d in absent) {
                mapped()
                val suffix=when(d.type) { "default","default,supl" -> "Internet"; "mms" -> "MMS"; "ims" -> "IMS";else -> "XCAP" }
                val binds=listOf("name:s:${if(plan.configMode==T2RuRules.APPLY) "T2 RU" else if(plan.configMode==MegafonRuRules.APPLY) "Megafon RU" else if(plan.configMode==BeelineRuRules.APPLY) "Beeline RU" else if(plan.configMode==MtsRuRules.APPLY) "MTS RU" else "MTS BY"} $suffix","apn:s:${d.apn}","numeric:s:${plan.sourceMcc}${plan.sourceMnc}","mcc:s:${plan.sourceMcc}","mnc:s:${plan.sourceMnc}","sub_id:i:${plan.subId}","type:s:${d.type}","authtype:i:${d.auth}","mmsc:s:${d.mmsc}","protocol:s:${d.protocol}","roaming_protocol:s:${d.protocol}","carrier_enabled:i:1","bearer_bitmask:i:${d.bearer}","user_visible:i:${d.visible}","user_editable:i:${d.editable}","user:s:${d.user}","password:s:${d.password}","mmsproxy:s:${d.mmsproxy}","mmsport:s:${d.mmsport}","edited:i:1","carrier_id:i:${if(plan.configMode==T2RuRules.APPLY) 1028 else if(plan.configMode==MegafonRuRules.APPLY) 1016 else if(plan.configMode==BeelineRuRules.APPLY) 1681 else if(plan.configMode==MtsRuRules.APPLY) 1678 else 569}")
                mutated=true
                execute(listOf("insert","--uri","content://telephony/carriers")+binds.flatMap { listOf("--bind",LocalPbApnRules.escapedBinding(it)) },user)
            }
            if(absent.isNotEmpty()) check(otherMissing(plan,mtsRows(plan,user,mapped)).isEmpty()) { "APN_READBACK_FAILED" }
            mapped()
            Bundle().apply { putBoolean("ok",true);putBoolean("mutated",mutated);putString("detail","native2:apn-shizuku:verified") }
        } catch(error:Exception) {
            val reason=if(error is SecurityException) "APN_ACCESS_DENIED" else error.message?.takeIf { it in setOf("EXISTING_APN_CONFLICT","APN_TIMEOUT","APN_REPLY_INVALID","APN_REPLY_TOO_LARGE","APN_READBACK_FAILED","APN_COMMAND_FAILED") } ?: "APN_CHECK_FAILED"
            Bundle().apply { putBoolean("ok",false);putBoolean("mutated",mutated);putString("detail","native2:apn-shizuku:$reason:mts") }
        }
    }
    private fun selectMts(plan:RegionPlan,user:Int,mapped:()->Unit):Boolean {
        val rows=mtsRows(plan,user,mapped)
        val uri="content://telephony/carriers/preferapn/subId/${plan.subId}"
        fun preferred():Long? { mapped();return LocalPbApnRules.preferredId(execute(listOf("query","--uri",uri,"--projection","_id"),user)) }
        val before=preferred()
        val id=if(plan.configMode==T2RuRules.APPLY) T2RuApnRules.internetId(rows,plan.subId,before) else if(plan.configMode==MegafonRuRules.APPLY) MegafonRuApnRules.internetId(rows,plan.subId,before) else if(plan.configMode==BeelineRuRules.APPLY) BeelineRuApnRules.internetId(rows,plan.subId,before) else if(plan.configMode==MtsRuRules.APPLY) MtsRuApnRules.internetId(rows,plan.subId,before) else MtsByApnRules.internetId(rows,plan.subId,before)
        if(before==id) return false
        mapped()
        execute(listOf("update","--uri",uri,"--bind","apn_id:l:$id"),user)
        check(preferred()==id) { "APN_READBACK_FAILED" }
        check(otherMissing(plan,mtsRows(plan,user,mapped)).isEmpty()) { "APN_READBACK_FAILED" }
        return true
    }
}

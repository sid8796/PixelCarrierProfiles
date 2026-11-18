package com.example.simregionswitcher

/** Presentation-only observation times; never drives a phone operation. */
class OperationTrace(val slot: Int,val subId: Int,val mcc: String,val mnc: String,val reset: Boolean,
    val restoreApn: Boolean,val startedWall: Long,val startedElapsed: Long,val configMode: String = "") {
    data class Event(val code: String,val wall: Long,val afterMs: Long)
    val events=mutableListOf<Event>()
    var outcome="RUNNING"; private set
    var durationMs: Long?=null; private set
    val active get()=outcome=="RUNNING"
    val executionObserved get()=events.any { it.code in setOf("APPLY","RESET","LOCAL_PB_APPLY","LOCAL_PB_CLEAR") }
    var awaitingApn=false
    fun record(code: String,wall: Long,elapsed: Long): Boolean {
        if(!active || events.any { it.code==code } || events.size>=24) return false
        events.add(Event(code,wall,(elapsed-startedElapsed).coerceAtLeast(0))); return true
    }
    fun finish(result: String,wall: Long,elapsed: Long): Boolean {
        if(!active || result !in setOf("SUCCESS","APN_ATTENTION","FAILED","INTERRUPTED")) return false
        record(result,wall,elapsed);outcome=result
        durationMs=if(result=="INTERRUPTED") null else (elapsed-startedElapsed).coerceAtLeast(0)
        return true
    }
    fun restore(events: List<Event>,result: String,duration: Long?,waiting: Boolean) {
        require(events.size<=24 && result in setOf("RUNNING","SUCCESS","APN_ATTENTION","FAILED","INTERRUPTED"))
        this.events.clear();this.events.addAll(events);outcome=result;durationMs=duration?.coerceAtLeast(0);awaitingApn=waiting
    }
}

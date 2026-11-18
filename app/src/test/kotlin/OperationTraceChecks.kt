import com.example.simregionswitcher.OperationTrace

fun operationTraceChecks(): Int {
    var n=0
    fun expect(ok: Boolean) { check(ok);n++ }
    val t=OperationTrace(0,3,"222","88",false,true,10000,500)
    expect(t.record("START",10000,500))
    expect(!t.record("START",10001,501))
    expect(t.record("WAIT",10002,499))
    expect(t.events.last().afterMs==0L)
    expect(t.finish("SUCCESS",12000,2500))
    expect(t.durationMs==2000L && !t.active)
    expect(!t.record("LATE",13000,3500))
    expect(!t.finish("FAILED",13000,3500) && t.outcome=="SUCCESS")
    val interrupted=OperationTrace(1,2,"260","03",false,false,10,100)
    expect(interrupted.finish("INTERRUPTED",200,300))
    expect(interrupted.durationMs==null)
    val failure=OperationTrace(0,3,"257","01",true,false,100,100)
    expect(!failure.finish("unknown",200,200) && failure.active)
    expect(failure.finish("FAILED",50,80) && failure.durationMs==0L)
    val restore=OperationTrace(0,3,"222","88",false,true,100,200)
    restore.restore(t.events.toList(),t.outcome,t.durationMs,false)
    expect(restore.events==t.events && restore.outcome=="SUCCESS" && restore.durationMs==2000L)
    return n
}

import com.example.simregionswitcher.*

fun apnChecks(): Int {
    var count=0
    fun expect(ok: Boolean) { check(ok) { "APN check ${count+1}" }; count++ }
    val saved=ApnRow(28,"Operator Internet","internet","25701","default,supl","IP","IP",true)
    val other=ApnRow(194,"Internet","internet.it","22288","default,supl","IPV4V6","IPV4V6",true)
    val current=ApnRow(144,"Operator","internet","","default","IPV4V6","IPV4V6",true)
    // Factory and user-created rows with the same APN address are NOT interchangeable.
    expect(ApnRules.candidate(saved,listOf(other,current))==null)
    expect(ApnRules.candidate(saved,listOf(other))==null)
    expect(ApnRules.candidate(current,listOf(other,current))==current)
    expect(ApnRules.candidate(saved,listOf(saved,current))==saved)
    val clone=saved.copy(id=145)
    expect(ApnRules.candidate(saved,listOf(clone))==clone)
    expect(ApnRules.candidate(saved,listOf(clone,clone.copy(id=146)))==null)
    expect(ApnRules.candidates(saved,listOf(clone,clone.copy(id=146))).size==2)
    expect(ApnRules.candidate(saved,listOf(saved,clone))==saved)
    // No source-MCC restriction: the chosen working row is the user's baseline.
    for (numeric in listOf("", "22288", "310260", "26003")) {
        val working=current.copy(numeric=numeric)
        expect(ApnRules.candidate(working,listOf(working))==working)
    }
    val changes=listOf(saved.copy(name="renamed"),saved.copy(apn="other"),saved.copy(numeric=""),
        saved.copy(type="default"),saved.copy(protocol="IPV4V6"),saved.copy(roaming="IPV4V6"),saved.copy(enabled=false))
    for (changed in changes) expect(ApnRules.candidate(saved,listOf(changed))==null)
    for(t in listOf("ims","xcap","mms","default,ims","default,xcap","default,emergency","ia"))
        expect(ApnRules.candidate(saved,listOf(current.copy(type=t)))==null)
    expect(ApnRules.candidate(saved.copy(enabled=false),listOf(saved))==null)
    for (reserved in listOf("ims","xcap","sos","emergency")) expect(!saved.copy(apn=reserved,type="*").internet())
    expect(ApnRules.candidate(saved.copy(apn="custom.apn",numeric="310260"),listOf(saved.copy(id=7,apn="custom.apn",numeric="310260")))!=null)
    val line="Row: 0 _id=144, name=Operator, apn=internet, numeric=, type=default,supl, protocol=IPV4V6, roaming_protocol=IPV4V6, carrier_enabled=1"
    expect(ApnRules.parse(line).single()==current.copy(type="default,supl"))
    expect(ApnRules.parse("No result found.").isEmpty())
    expect(ApnRules.parse(line.replace("numeric=,","numeric=NULL,")).single().numeric=="")
    expect(ApnRules.parse(line.replace("Operator, apn=", "Operator, Inc., apn=")).single().name=="Operator, Inc.")
    for(s in listOf("Permission Denial",line+"\n"+line,line.replace("_id=144","_id=-1"),line.replace("carrier_enabled=1","carrier_enabled=2"),line.replace("name=Operator","name=Operator, numeric=bad"),line.replace("apn=internet, ","")))
        expect(runCatching { ApnRules.parse(s) }.isFailure)
    expect(ApnRules.endedAfterReboot(10,11))
    for(pair in listOf(-1 to 2, 2 to -1, 2 to 2, 3 to 2)) expect(!ApnRules.endedAfterReboot(pair.first,pair.second))
    for(result in listOf("APN_PENDING","APN_NOT_TRACKED","APN_READ_FAILED","INVALID_REPLY","")) {
        expect(ApnRules.uncertain(true,false,result)); expect(ApnRules.uncertain(false,true,result))
    }
    for(result in listOf("APN_VERIFIED","APN_NOT_SELECTED","APN_PRECHECK_FAILED","APN_TICKET_EXPIRED"))
        expect(!ApnRules.uncertain(true,true,result))
    expect(!ApnRules.uncertain(false,false,"APN_READ_FAILED"))
    return count
}

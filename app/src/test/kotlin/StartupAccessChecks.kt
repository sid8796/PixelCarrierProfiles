import com.example.simregionswitcher.*

fun startupAccessChecks():Int {
    var count=0
    fun expect(ok:Boolean) { check(ok) { "startup access case ${count+1}" };count++ }
    val phonePrompt=StartupAccessRules.Prompt.PHONE
    val shizukuPrompt=StartupAccessRules.Prompt.SHIZUKU
    val none=StartupAccessRules.Prompt.NONE
    // No request in the background or while either permission dialog is active.
    expect(StartupAccessRules.next(false,false,false,false,"SHIZUKU_NOT_AUTHORIZED",false)==none)
    expect(StartupAccessRules.next(false,false,true,true,"SHIZUKU_NOT_AUTHORIZED",false)==none)
    expect(StartupAccessRules.next(true,true,true,true,"SHIZUKU_NOT_AUTHORIZED",false)==none)
    // Existing grants skip the phone dialog. Stopped/old/unavailable Shizuku cannot display its dialog.
    expect(StartupAccessRules.next(true,false,true,false,null,false)==none)
    expect(StartupAccessRules.next(true,false,true,false,"SHIZUKU_NOT_AUTHORIZED",false)==shizukuPrompt)
    for(access in listOf("SHIZUKU_NOT_RUNNING","SHIZUKU_TOO_OLD","SHIZUKU_UNAVAILABLE")) {
        expect(StartupAccessRules.next(true,false,true,true,access,false)==none)
        expect(StartupAccessRules.next(true,false,false,false,access,false)==phonePrompt)
    }
    // Clean install: phone first, no overlapping dialog, Shizuku after grant; no repeats after denial.
    expect(StartupAccessRules.next(true,false,false,false,"SHIZUKU_NOT_AUTHORIZED",false)==StartupAccessRules.Prompt.PHONE)
    expect(StartupAccessRules.next(true,true,false,true,"SHIZUKU_NOT_AUTHORIZED",false)==StartupAccessRules.Prompt.NONE)
    expect(StartupAccessRules.next(true,false,true,true,"SHIZUKU_NOT_AUTHORIZED",false)==StartupAccessRules.Prompt.SHIZUKU)
    expect(StartupAccessRules.next(true,false,true,true,"SHIZUKU_NOT_AUTHORIZED",true)==StartupAccessRules.Prompt.NONE)
    expect(StartupAccessRules.next(true,false,false,true,"SHIZUKU_NOT_AUTHORIZED",false)==StartupAccessRules.Prompt.NONE)
    return count
}

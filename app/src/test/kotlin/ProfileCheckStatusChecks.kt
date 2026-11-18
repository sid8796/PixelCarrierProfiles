import com.example.simregionswitcher.*

fun profileCheckStatusChecks():Int {
    var count=0
    fun expect(ok:Boolean) { check(ok) { "profile check case ${count+1}" };count++ }
    val notChecked=ProfileOverviewRules.CheckState.NOT_CHECKED
    val active=ProfileOverviewRules.CheckState.ACTIVE
    val different=ProfileOverviewRules.CheckState.DIFFERENT
    val failed=ProfileOverviewRules.CheckState.FAILED
    for((mts,ru) in listOf(false to false,true to false,false to true)) {
        val expected=ProfileOverviewRules.expected(mts,ru)
        expect(expected.isNotEmpty())
        expect(ProfileOverviewRules.checkState(null,mts,ru)==notChecked)
        expect(ProfileOverviewRules.checkState(expected,mts,ru)==active)
        expect(ProfileOverviewRules.differences(expected,mts,ru).isEmpty())
        // A valid Android response can omit vendor keys after reboot. This is a mismatch, not an unread state.
        expect(ProfileOverviewRules.checkState(emptyMap(),mts,ru)==different)
        expect(ProfileOverviewRules.checkState(expected.keys.associateWith { null },mts,ru)==different)
        expect(ProfileOverviewRules.checkState(mapOf("carrier_volte_available_bool" to false),mts,ru)==different)
        for(error in listOf("PHONE_PERMISSION_REQUIRED","CONFIG_ACCESS_DENIED","CONFIG_UNAVAILABLE","CONFIG_READ_FAILED")) {
            expect(ProfileOverviewRules.checkState(null,mts,ru,error)==failed)
            expect(ProfileOverviewRules.checkState(expected,mts,ru,error)==failed)
        }
        for(key in expected.keys) {
            val missing=expected-key
            expect(ProfileOverviewRules.checkState(missing,mts,ru)==different)
            val diff=ProfileOverviewRules.differences(missing,mts,ru)
            expect(diff.size==1 && diff[0].key==key && diff[0].expected==expected[key] && diff[0].actual==null)
        }
        // Extra Android keys are allowed, wrong scalar types are not treated as matching.
        expect(ProfileOverviewRules.checkState(expected+mapOf("android_extra_key" to 900),mts,ru)==active)
        expect(ProfileOverviewRules.checkState(expected+mapOf("carrier_volte_available_bool" to 1),mts,ru)==different)
        expect(ProfileOverviewRules.checkState(expected+mapOf("carrier_volte_available_bool" to "true"),mts,ru)==different)
    }
    val afterReboot=mapOf<String,Any?>("carrier_volte_available_bool" to false,"carrier_wfc_ims_available_bool" to false,"carrier_supports_ss_over_ut_bool" to false,"hide_lte_plus_data_icon_bool" to false)
    expect(ProfileOverviewRules.checkState(afterReboot)==different)
    expect(ProfileOverviewRules.differences(afterReboot).any { it.key==LocalPbRules.XCAP_SERVER_KEY && it.actual==null })
    expect(ProfileOverviewRules.differences(afterReboot).any { it.key=="carrier_volte_available_bool" && it.actual==false })
    val mtsRu=ProfileOverviewRules.expected(ru=true)
    expect(ProfileOverviewRules.checkState(mtsRu+mapOf("carrier_ussd_method_int" to true),ru=true)==different)
    for(id in listOf("a1_by","mts_by","mts_ru","beeline_ru","megafon_ru","t2_ru")) {
        val wanted=ProfileOverviewRules.expectedFor(id)
        expect(ProfileOverviewRules.checkStateFor(wanted,id)==active)
        expect(ProfileOverviewRules.checkStateFor(null,id)==notChecked)
        expect(ProfileOverviewRules.checkStateFor(wanted,id,"CONFIG_ACCESS_DENIED")==failed)
        for(key in wanted.keys) {
            expect(ProfileOverviewRules.checkStateFor(wanted-key,id)==different)
            expect(ProfileOverviewRules.differencesFor(wanted-key,id).single().key==key)
            expect(ProfileOverviewRules.checkStateFor(wanted+mapOf(key to "incorrect-type-or-value"),id)==different)
        }
    }
    return count
}

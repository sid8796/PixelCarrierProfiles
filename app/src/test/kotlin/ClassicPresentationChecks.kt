import com.example.simregionswitcher.OperatorCatalog
import com.example.simregionswitcher.RadioDisplayRules

fun classicPresentationChecks(): Int {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    expect(OperatorCatalog.operators.size==2265)
    expect(OperatorCatalog.profileCount==629)
    expect(OperatorCatalog.countries.size==129)
    expect(OperatorCatalog.operators.map { it.profile }.distinct().size==629)
    expect(OperatorCatalog.operators.none { it.profile in setOf("others","default","no_sim","carrier_list") })
    expect(OperatorCatalog.operators.map { Triple(it.profile,it.mcc,it.mnc) }.distinct().size==OperatorCatalog.operators.size)
    expect(OperatorCatalog.findSelectable("257","01")==null)
    expect(OperatorCatalog.find("257","01")==null)
    expect(OperatorCatalog.operators.any { it.profile=="windtre_it" && it.mcc=="222" && it.mnc=="88" })
    expect(OperatorCatalog.operators.any { it.profile=="orange_pl" && it.mcc=="260" && it.mnc=="03" })
    expect(OperatorCatalog.operators.any { it.profile=="telcel_mx" && it.mcc=="334" && it.mnc=="020" })
    expect(OperatorCatalog.operators.any { it.conditional })
    expect(OperatorCatalog.operators.groupBy { it.mcc to it.mnc }.any { it.value.size>1 })
    for(entry in OperatorCatalog.operators) {
        expect(entry.mcc.matches(Regex("[0-9]{3}")) && entry.mnc.matches(Regex("[0-9]{2,3}")))
        expect(entry.country.matches(Regex("[A-Z]{2}")) && entry.name==entry.profile)
        expect(OperatorCatalog.find(entry.mcc,entry.mnc)!=null)
    }
    expect(OperatorCatalog.find("257","1")==null)
    expect(OperatorCatalog.countries==OperatorCatalog.countries.distinct().sorted())
    for(country in OperatorCatalog.countries) expect(OperatorCatalog.operators.any { it.country==country })
    expect(RadioDisplayRules.family(13) == "4G")
    expect(RadioDisplayRules.family(20) == "5G NR")
    expect(RadioDisplayRules.family(3) == "3G")
    expect(RadioDisplayRules.family(2) == "2G")
    expect(RadioDisplayRules.family(0) == null)
    expect(RadioDisplayRules.family(18) == null) // IWLAN must not be labelled cellular LTE.
    expect(RadioDisplayRules.family(-1) == null)
    expect(RadioDisplayRules.family(999) == null)
    expect(RadioDisplayRules.dbm(-109) == -109)
    expect(RadioDisplayRules.dbm(Int.MAX_VALUE) == null)
    expect(RadioDisplayRules.dbm(0) == null)
    expect(RadioDisplayRules.dbm(null) == null)
    expect(RadioDisplayRules.dbm(-200) == null)
    expect(RadioDisplayRules.level(0) == 0)
    expect(RadioDisplayRules.level(4) == 4)
    expect(RadioDisplayRules.level(null) == 0)
    expect(RadioDisplayRules.level(99) == 0)
    expect(RadioDisplayRules.level(-1) == 0)
    println("Catalog: ${OperatorCatalog.operators.size} entries, ${OperatorCatalog.countries.size} countries")
    return count
}

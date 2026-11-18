import com.example.simregionswitcher.ImsRefreshRules
fun imsRefreshChecks(): Int {
    val cases=listOf(
        ImsRefreshRules.readNumber(1,1,true,true,false,false) to true,
        ImsRefreshRules.readNumber(1,2,true,true,false,false) to false,
        ImsRefreshRules.readNumber(0,0,true,true,false,false) to false,
        ImsRefreshRules.readNumber(1,1,false,true,false,false) to false,
        ImsRefreshRules.readNumber(1,1,true,false,false,false) to false,
        ImsRefreshRules.readNumber(1,1,true,true,true,false) to false,
        ImsRefreshRules.readNumber(1,1,true,true,false,true) to false)
    cases.forEach { (actual, expected) -> check(actual==expected) }
    return cases.size
}

import com.example.simregionswitcher.LocalCarrierAuthorization as Auth
import com.example.simregionswitcher.RegionPlan
fun localAuthorizationChecks(): Int {
    var n = 0
    fun expect(value: Boolean) { check(value); n++ }
    val p = RegionPlan(0, 3, "A1", "257", "01", false, "222", "88")
    expect(!Auth.consume("unarmed", p))
    Auth.arm("first", p)
    expect(!Auth.consume("wrong", p))
    expect(!Auth.consume("first", p.copy(slot=1)))
    expect(!Auth.consume("first", p.copy(subId=4)))
    expect(!Auth.consume("first", p.copy(mnc="088")))
    expect(Auth.consume("first", p))
    expect(!Auth.consume("first", p))
    Auth.arm("old", p); Auth.arm("new", p.copy(subId=2))
    expect(!Auth.consume("old", p))
    expect(Auth.consume("new", p.copy(subId=2)))
    expect(!Auth.consume("new", p.copy(subId=2)))
    return n
}

import com.example.simregionswitcher.*

fun main() {
    fun expect(label: String, expected: SlotMapping, actual: SlotMapping) {
        check(expected == actual) { "$label: expected=$expected actual=$actual" }
    }
    val dual = listOf(SimRecord(0, 3), SimRecord(1, 2))
    expect("slot is not subId", SlotMapping(MappingState.OBSERVED, 3), SlotResolver.resolve(0, dual))
    expect("second slot", SlotMapping(MappingState.OBSERVED, 2), SlotResolver.resolve(1, dual))
    expect("permission/unavailable", SlotMapping(MappingState.UNAVAILABLE), SlotResolver.resolve(0, null))
    expect("empty slot", SlotMapping(MappingState.EMPTY_SLOT), SlotResolver.resolve(1, listOf(SimRecord(0, 3))))
    expect("duplicate slot", SlotMapping(MappingState.AMBIGUOUS), SlotResolver.resolve(0, listOf(SimRecord(0, 3), SimRecord(0, 4))))
    expect("duplicate subId", SlotMapping(MappingState.AMBIGUOUS), SlotResolver.resolve(0, listOf(SimRecord(0, 3), SimRecord(1, 3))))
    expect("invalid subId", SlotMapping(MappingState.AMBIGUOUS), SlotResolver.resolve(0, listOf(SimRecord(0, -1))))
    expect("invalid slot", SlotMapping(MappingState.UNAVAILABLE), SlotResolver.resolve(-1, dual))
    expect("hot swap changes snapshot", SlotMapping(MappingState.OBSERVED, 9), SlotResolver.resolve(0, listOf(SimRecord(0, 9))))
    for (mnc in listOf("01", "001", "88")) check(ProfileValidator.isValid(CarrierProfile("", "222", mnc)))
    for (mnc in listOf("", "8", "0000", " 01", "a1", "０１")) check(!ProfileValidator.isValid(CarrierProfile("", "222", mnc)))
    for (mcc in listOf("", "22", "2222", "22a", " 222")) check(!ProfileValidator.isValid(CarrierProfile("", mcc, "88")))
    val protocolChecks = readerProtocolChecks()
    val binderChecks = binderMappingChecks()
    val archiveChecks = readArchiveChecks()
    val transportChecks = transportChecks()
    val snapshotChecks = snapshotChecks()
    val capabilityChecks = capabilityChecks()
    val projectionChecks = payloadProjectionChecks()
    val numberChecks = imsNumberChecks()
    val trialChecks = a1TrialChecks()
    val recoveryChecks = trialRecoveryChecks()
    val regionChecks = regionPlanChecks()
    val classicChecks = classicPresentationChecks()
    val apnChecks = apnChecks()
    val autoApnChecks = apnAutoSaveChecks()
    val imsRefreshChecks = imsRefreshChecks()
    val directChecks = directApplyChecks()
    val nativeChecks = nativeCarrierChecks()
    val authorizationChecks = localAuthorizationChecks()
    val profileDisplayChecks=profileDisplayChecks()
    val operationChecks=operationTraceChecks()
    val leaseChecks = permissionLeaseChecks()
    println("PASS: ${beelineRuChecks()} Beeline binding/APN/profile/status isolation cases")
    println("PASS: ${startupAccessChecks()} startup permission sequence/lifecycle/denial cases")
    println("PASS: ${megafonRuChecks()} Megafon profile/binding/APN/status cases")
    println("PASS: ${t2RuChecks()} T2 profile/binding/APN/status cases")
    val localPbCount = localPbChecks()
    println("PASS: ${profileCheckStatusChecks()} profile status/reboot/missing-key/read-error cases")
    println("PASS: ${persistentConfigChecks()} temporary CarrierConfig persistence-rejection/reset/isolation/diagnostic cases")
    println("PASS: $localPbCount local PB parsing/target/authorization/APN isolation, 9 mapping, 14 profile, $protocolChecks reader protocol and $binderChecks helper mapping/protocol, $archiveChecks snapshot/lifecycle, $transportChecks transport, $snapshotChecks persistence/batch, $capabilityChecks capability, $projectionChecks payload projection, $numberChecks IMS number, $trialChecks A1 trial, $recoveryChecks late result/reboot, $regionChecks dual-SIM plan, $classicChecks catalog/radio presentation cases, $apnChecks APN selection/parser cases, $autoApnChecks APN auto-save cases, $imsRefreshChecks IMS refresh lifecycle cases, $directChecks direct-apply deduplication cases, $nativeChecks native transaction cases, $authorizationChecks local authorization cases, $leaseChecks permission lease cases, $operationChecks operation timeline cases, $profileDisplayChecks profile presentation cases")
}










import com.example.simregionswitcher.TrialCompletion
import com.example.simregionswitcher.TrialRecoveryRules
import java.util.concurrent.CountDownLatch

fun trialRecoveryChecks(): Int {
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    val old = TrialCompletion<String>("old", "prepare")
    expect(old.peek() == null) // The caller's local wait expired; slot is still pending.
    expect(old.finish("late snapshot"))
    expect(old.peek() == "late snapshot")
    expect(old.peek() == "late snapshot") // Inspect does not consume or restart the call.
    expect(!old.finish("duplicate"))
    expect(old.peek() == "late snapshot")
    val fresh = TrialCompletion<String>("fresh", "prepare")
    expect(fresh.peek() == null) // Old callback cannot populate a different request.
    expect(!TrialRecoveryRules.accepts("fresh", "prepare", old.requestId, old.phase))
    expect(TrialRecoveryRules.accepts("old", "prepare", old.requestId, old.phase))
    expect(!TrialRecoveryRules.accepts("old", "apply", old.requestId, old.phase))
    expect(!TrialRecoveryRules.accepts("", "prepare", "", "prepare"))
    expect(!TrialRecoveryRules.accepts("old", "prepare", null, "prepare"))
    expect(!TrialRecoveryRules.accepts("old", "prepare", "old", null))
    expect(!TrialRecoveryRules.accepts("old", "reset", "old", "reset"))
    expect(TrialRecoveryRules.afterReboot("prepare", 5, 6))
    expect(!TrialRecoveryRules.afterReboot("prepare", 5, 5))
    expect(!TrialRecoveryRules.afterReboot("prepare", 6, 5))
    expect(!TrialRecoveryRules.afterReboot("prepare", -1, 5))
    expect(!TrialRecoveryRules.afterReboot("prepare", 5, -1))
    expect(!TrialRecoveryRules.afterReboot("apply", 5, 6))
    expect(!TrialRecoveryRules.afterReboot("apply", 0, 999))
    expect(!TrialRecoveryRules.afterReboot("", 5, 6))
    val race = TrialCompletion<String>("same", "prepare")
    val start = CountDownLatch(1)
    val winners = java.util.concurrent.atomic.AtomicInteger()
    val workers = listOf("one", "two").map { value -> Thread { start.await(); if (race.finish(value)) winners.incrementAndGet() } }
    workers.forEach { it.start() }; start.countDown(); workers.forEach { it.join(2000) }
    expect(workers.none { it.isAlive })
    expect(winners.get() == 1)
    expect(race.peek() in setOf("one", "two"))
    return count
}

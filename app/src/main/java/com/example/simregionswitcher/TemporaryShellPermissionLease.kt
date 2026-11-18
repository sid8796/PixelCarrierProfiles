package com.example.simregionswitcher

/** Release only a successfully acquired lease; never clear another instrumentation's lease. */
object TemporaryShellPermissionLease {
    fun <T> run(acquire: () -> Unit, release: () -> Unit, block: () -> T): T {
        acquire()
        return try { block() } finally { release() }
    }
}

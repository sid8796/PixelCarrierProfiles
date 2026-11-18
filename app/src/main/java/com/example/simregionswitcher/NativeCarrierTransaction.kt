package com.example.simregionswitcher

/** A transaction is never retried: even a failed Binder return can follow a successful write. */
object NativeCarrierTransaction {
    interface Port {
        fun preflight()
        fun mapping()
        fun overrideConfig(reset: Boolean)
        fun identity(reset: Boolean)
    }
    data class Result(val code: String, val stage: String, val error: String? = null)
    fun execute(reset: Boolean, port: Port, progress: (String) -> Unit = {}): Result {
        var enteredWrite = false
        var stage = "preflight"
        return try {
            port.preflight(); port.mapping()
            stage = "carrier-config"; progress(stage)
            enteredWrite = true; port.overrideConfig(reset)
            stage = "mapping-after-config"; port.mapping()
            stage = "carrier-identity"; progress(stage); port.identity(reset)
            Result("MODIFIER_REPORTED_SUCCESS", "returned")
        } catch (error: Exception) {
            val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
            Result(if (enteredWrite) "OUTCOME_UNCERTAIN" else "PRECHECK_FAILED", stage, cause.javaClass.simpleName)
        }
    }
}

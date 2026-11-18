package com.example.simregionswitcher

import android.content.Context
import android.content.pm.PackageManager

object ReaderTargetGate {
    fun check(context: Context, operation: ReadOperation): String? {
        return try {
            val pkg = context.packageManager.getPackageInfo(ReaderProtocol.PACKAGE,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_INSTRUMENTATION.toLong()))
            when {
                !ReaderProtocol.supported(pkg.versionName, pkg.longVersionCode) -> "UNREVIEWED_VERSION"
                pkg.applicationInfo?.enabled != true -> "TARGET_DISABLED"
                pkg.instrumentation.orEmpty().none {
                    it.name == operation.className && it.targetPackage == ReaderProtocol.PACKAGE
                } -> "READER_NOT_DECLARED"
                else -> null
            }
        } catch (_: PackageManager.NameNotFoundException) { "TARGET_NOT_FOUND" }
        catch (_: SecurityException) { "TARGET_ACCESS_DENIED" }
        catch (_: RuntimeException) { "TARGET_LOOKUP_FAILED" }
    }
}

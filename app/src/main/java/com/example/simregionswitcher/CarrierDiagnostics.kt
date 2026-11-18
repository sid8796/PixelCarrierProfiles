package com.example.simregionswitcher

import android.content.Context
import android.content.pm.PackageManager

/** Only package metadata reads. This class never launches or binds a component. */
class CarrierDiagnostics(private val context: Context) {
    fun read(onEvent: (String) -> Unit): String {
        val pm = context.packageManager
        val unknown = context.getString(R.string.unknown)
        val sections = mutableListOf<String>()
        sections += context.getString(R.string.snapshot_time, java.text.DateFormat.getDateTimeInstance().format(java.util.Date()))
        try {
            val flags = PackageManager.GET_INSTRUMENTATION or PackageManager.GET_SERVICES or
                PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS or
                PackageManager.MATCH_DISABLED_COMPONENTS
            val pkg = pm.getPackageInfo(CARRIER_PACKAGE, PackageManager.PackageInfoFlags.of(flags.toLong()))
            sections += context.getString(R.string.carrier_found, CARRIER_PACKAGE,
                pkg.versionName ?: unknown, state(pkg.applicationInfo?.enabled == true))
            sections += context.getString(R.string.package_metadata, pkg.longVersionCode, pkg.applicationInfo?.targetSdkVersion ?: -1)
            onEvent(if (pkg.applicationInfo?.enabled == true) "CARRIER_FOUND_ENABLED" else "CARRIER_FOUND_DISABLED")
            val instrumentation = pkg.instrumentation.orEmpty()
            sections += context.getString(R.string.instrumentation_heading)
            sections += if (instrumentation.isEmpty()) context.getString(R.string.none_declared)
                else instrumentation.joinToString("\n") {
                    context.getString(R.string.instrumentation_row, it.packageName, it.name, it.targetPackage ?: unknown)
                }
            sections += context.getString(if (instrumentation.any { it.name == EXPECTED_INSTRUMENTATION })
                R.string.expected_declared else R.string.expected_not_declared, EXPECTED_INSTRUMENTATION)
            sections += context.getString(R.string.services_heading)
            sections += pkg.services.orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n") {
                context.getString(R.string.component_row, it.name, state(it.enabled), yesNo(it.exported), it.permission ?: context.getString(R.string.none))
            } ?: context.getString(R.string.none_declared)
            sections += context.getString(R.string.activities_heading)
            sections += pkg.activities.orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n") {
                context.getString(R.string.component_row, it.name, state(it.enabled), yesNo(it.exported), it.permission ?: context.getString(R.string.none))
            } ?: context.getString(R.string.none_declared)
            sections += context.getString(R.string.permissions_heading)
            sections += pkg.requestedPermissions.orEmpty().joinToString("\n").ifEmpty { context.getString(R.string.none_declared) }
            sections += context.getString(R.string.metadata_limit)
        } catch (_: PackageManager.NameNotFoundException) {
            sections += context.getString(R.string.carrier_missing, CARRIER_PACKAGE)
            onEvent("CARRIER_NOT_FOUND_CURRENT_USER")
        } catch (_: SecurityException) {
            sections += context.getString(R.string.carrier_denied)
            onEvent("CARRIER_ACCESS_DENIED")
        } catch (error: RuntimeException) {
            sections += context.getString(R.string.carrier_error, error.javaClass.simpleName)
            onEvent("CARRIER_CHECK_ERROR")
        }
        // A separate lookup: its failure must not overwrite the Carrier IMS result.
        try {
            val shizuku = pm.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            sections += context.getString(R.string.shizuku_found, state(shizuku.applicationInfo?.enabled == true))
            onEvent("SHIZUKU_PACKAGE_FOUND")
        } catch (_: PackageManager.NameNotFoundException) {
            sections += context.getString(R.string.shizuku_missing)
            onEvent("SHIZUKU_PACKAGE_NOT_FOUND")
        } catch (error: RuntimeException) {
            sections += context.getString(R.string.shizuku_unknown, error.javaClass.simpleName)
            onEvent("SHIZUKU_LOOKUP_ERROR")
        }
        sections += context.getString(R.string.stage2_limits)
        return sections.joinToString("\n\n")
    }

    private fun state(enabled: Boolean) = context.getString(if (enabled) R.string.enabled else R.string.disabled)
    private fun yesNo(value: Boolean) = context.getString(if (value) R.string.yes else R.string.no)

    companion object {
        const val CARRIER_PACKAGE = "io.github.vvb2060.ims.mod"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val EXPECTED_INSTRUMENTATION = "io.github.vvb2060.ims.privileged.ImsModifier"
    }
}

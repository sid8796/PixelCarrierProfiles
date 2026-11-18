package com.example.simregionswitcher

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import java.security.MessageDigest

/** Immutable bundled experiment; the UI and writer verify the same bytes. No external-file execution. */
object LocalPbProfile {
    const val SHA256="cead43f4908ffaf29b3df54740d2cbd659b7a75f1510fee5afc10b7f2f00290c"
    fun load(context: Context,id:String="a1_by"): LocalPbRules.Profile {
        require(id in setOf("a1_by","mts_by","mts_ru","beeline_ru","megafon_ru","t2_ru"))
        val bytes=context.assets.open("$id.pb").use { it.readNBytes(4097) }
        check(bytes.size<=4096)
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        check(id in setOf("a1_by","mts_by","mts_ru","beeline_ru","megafon_ru","t2_ru") && hash==when(id) { "t2_ru" -> T2RuRules.SHA256; "megafon_ru" -> MegafonRuRules.SHA256; "beeline_ru" -> BeelineRuRules.SHA256; "mts_by" -> MtsByRules.SHA256; "mts_ru" -> MtsRuRules.SHA256; else -> SHA256 }) { "PROFILE_CHANGED" }
        return LocalPbRules.decode(bytes)
    }
    fun bundle(context: Context,id:String="a1_by")=PersistableBundle().apply {
        val profile=load(context,id)
        profile.flags.forEach { (key,value) -> putBoolean(key,value) }
        profile.integers.forEach { (key,value) -> putInt(key,value) }
        profile.strings.forEach { (key,value) -> putString(key,value) }
        profile.arrays.forEach { (key,value) -> putIntArray(key,value.toIntArray()) }
    }
    data class ReadResult(val values:Map<String,Any?>,val error:String?=null)
    @Suppress("DEPRECATION") fun read(context:Context,subId:Int,id:String="a1_by"):ReadResult {
        val profile=load(context,id)
        val allKeys=profile.flags.keys+profile.integers.keys+profile.strings.keys+profile.arrays.keys
        fun failed(reason:String)=ReadResult(allKeys.associateWith { null },reason)
        if(context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            return failed("PHONE_PERMISSION_REQUIRED")
        val config=try {
            context.getSystemService(CarrierConfigManager::class.java)?.getConfigForSubId(subId)
        } catch (_:SecurityException) { return failed("CONFIG_ACCESS_DENIED") }
          catch (_:RuntimeException) { return failed("CONFIG_READ_FAILED") }
        if(config==null) return failed("CONFIG_UNAVAILABLE")
        val values=try { allKeys.associateWith { key -> when(key) {
            in profile.arrays -> (config.get(key) as? IntArray)?.toList()
            in profile.integers -> config.get(key) as? Int
            in profile.strings -> config.get(key) as? String
            else -> config.get(key) as? Boolean
        } } } catch (_:RuntimeException) { return failed("CONFIG_READ_FAILED") }
        return ReadResult(values)
    }
    fun effective(context:Context,subId:Int,id:String="a1_by"):Map<String,Any?> = read(context,subId,id).values
    fun readback(context: Context,subId: Int,id:String="a1_by"): Boolean {
        val profile=load(context,id)
        val expected:Map<String,Any> = profile.flags+profile.integers+profile.strings+profile.arrays
        val actual=effective(context,subId,id)
        return expected.all { (key,value) -> actual[key]==value }
    }
}

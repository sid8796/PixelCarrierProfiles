package com.example.simregionswitcher

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList

/** Set the requested English default once; later respect both app and Android language choices. */
class SwitcherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs=getSharedPreferences("app_language",MODE_PRIVATE)
        if(!prefs.getBoolean("initialized",false)) {
            val manager=getSystemService(LocaleManager::class.java)
            if(manager.applicationLocales.isEmpty) manager.applicationLocales=LocaleList.forLanguageTags("en")
            prefs.edit().putBoolean("initialized",true).apply()
        }
    }
}

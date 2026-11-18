package com.example.simregionswitcher

import android.content.Context
import android.content.res.Configuration

/** Resolve explicit theme before Android creates the Activity theme and views. */
object AppearanceContext {
    fun wrap(base: Context): Context {
        val choice=base.getSharedPreferences("pixel_appearance",Context.MODE_PRIVATE).getString("theme","system")
        if(choice !in setOf("light","dark")) return base
        val config=Configuration(base.resources.configuration)
        config.uiMode=(config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if(choice=="dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(config)
    }
}

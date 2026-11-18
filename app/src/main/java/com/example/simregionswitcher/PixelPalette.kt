package com.example.simregionswitcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Uses Android's wallpaper-derived tonal resources; no privileged access or network. */
data class PixelPalette(val dark: Boolean, val page: Int, val surface: Int, val tonal: Int,
    val ink: Int, val secondary: Int, val primary: Int, val onPrimary: Int,
    val outline: Int, val success: Int, val successSurface: Int, val error: Int, val errorSurface: Int,
    val tertiary: Int) {
    companion object {
        fun from(context: Context, dynamic: Boolean): PixelPalette {
            val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            fun color(id: Int)=context.getColor(id)
            return PixelPalette(dark,
                color(if(dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10),
                color(if(dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_0),
                color(if(dark) android.R.color.system_neutral2_800 else android.R.color.system_neutral2_100),
                color(if(dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
                color(if(dark) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700),
                if(dynamic) color(if(dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600) else if(dark) 0xFFADC6FF.toInt() else 0xFF225EA8.toInt(),
                if(dynamic) color(if(dark) android.R.color.system_accent1_900 else android.R.color.system_accent1_0) else if(dark) 0xFF003063.toInt() else Color.WHITE,
                color(if(dark) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_200),
                if(dark) 0xFFA5DCB6.toInt() else 0xFF226B3D.toInt(),
                if(dark) 0xFF163C27.toInt() else 0xFFD7F2DF.toInt(),
                if(dark) 0xFFFFB4AB.toInt() else 0xFF9B2926.toInt(),
                if(dark) 0xFF542724.toInt() else 0xFFFFDAD6.toInt(),
                color(if(dark) android.R.color.system_accent3_200 else android.R.color.system_accent3_600))
        }
    }
}

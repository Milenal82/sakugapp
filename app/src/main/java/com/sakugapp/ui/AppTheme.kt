// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

internal fun appColors(light:Boolean):ColorScheme = if(light) lightColorScheme(
    primary=Color(0xFF315EA8),onPrimary=Color.White,background=Color(0xFFF5F6F8),surface=Color.White,
    surfaceContainer=Color.White,surfaceContainerHigh=Color(0xFFECEEF2),surfaceVariant=Color(0xFFECEEF2),
    outlineVariant=Color(0xFFD9DDE4),onBackground=Color(0xFF20232A),onSurface=Color(0xFF20232A),onSurfaceVariant=Color(0xFF626873),
) else darkColorScheme(primary=Color(0xFF91B7FF),onPrimary=Color(0xFF142B4D),background=Color(0xFF101114),surface=Color(0xFF1B1D22),
    surfaceContainer=Color(0xFF1B1D22),surfaceContainerHigh=Color(0xFF24262C),surfaceVariant=Color(0xFF24262C),outlineVariant=Color(0xFF303238),
    onBackground=Color(0xFFF2F3F5),onSurface=Color(0xFFF2F3F5),onSurfaceVariant=Color(0xFFA6A9B2))
internal fun copyrightColor(light:Boolean)=if(light)Color(0xFF926000)else Color(0xFFFFD166)
internal fun artistColor(light:Boolean)=if(light)Color(0xFF7542B8)else Color(0xFFC59BFF)
internal fun scoreColor(light:Boolean)=if(light)Color(0xFFC62828)else Color(0xFFFF5252)
internal fun themeIsLight(mode:String,systemDark:Boolean)=mode=="light" || (mode=="system" && !systemDark)
internal object Motion { const val search=340;const val controls=320;const val history=300;const val folder=280;const val press=150 }
internal object GridPolicy {
    fun options(smallestWidthDp:Int)=if(smallestWidthDp>=600)com.sakugapp.data.PreferenceRules.columnOptions else listOf(1,2,3,4,6)
    fun effective(requested:Int,smallestWidthDp:Int)=if(requested in options(smallestWidthDp))requested else if(requested in listOf(8,12))4 else 2
}
internal fun mediaTime(milliseconds:Long):String { val seconds=milliseconds.coerceAtLeast(0)/1000;return if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)else"%d:%02d".format(seconds/60,seconds%60) }

@Composable fun SakugappTheme(light:Boolean,content:@Composable ()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity
    DisposableEffect(activity,light) {
        val style=if(light)SystemBarStyle.light(AndroidColor.TRANSPARENT,AndroidColor.TRANSPARENT)else SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
        onDispose { }
    }
    MaterialTheme(colorScheme=appColors(light),content=content)
}

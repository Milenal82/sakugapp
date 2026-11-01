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
import androidx.compose.ui.unit.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle

internal fun appColors(light:Boolean,accent:String="default"):ColorScheme {
    val base=if(light) lightColorScheme(
    primary=Color(0xFF315EA8),onPrimary=Color.White,background=Color(0xFFF5F6F8),surface=Color.White,
    surfaceContainer=Color.White,surfaceContainerHigh=Color(0xFFECEEF2),surfaceVariant=Color(0xFFECEEF2),
    outlineVariant=Color(0xFFD9DDE4),onBackground=Color(0xFF20232A),onSurface=Color(0xFF20232A),onSurfaceVariant=Color(0xFF626873),
) else darkColorScheme(primary=Color(0xFF91B7FF),onPrimary=Color(0xFF142B4D),background=Color(0xFF101114),surface=Color(0xFF1B1D22),
    surfaceContainer=Color(0xFF1B1D22),surfaceContainerHigh=Color(0xFF24262C),surfaceVariant=Color(0xFF24262C),outlineVariant=Color(0xFF303238),
    onBackground=Color(0xFFF2F3F5),onSurface=Color(0xFFF2F3F5),onSurfaceVariant=Color(0xFFA6A9B2))
    return when(accent){
        "blue"->base.copy(primary=Color(if(light)0xFF006879 else 0xFF79D4E8),onPrimary=Color(if(light)0xFFFFFFFF else 0xFF00363F),
            primaryContainer=Color(if(light)0xFFB5ECF7 else 0xFF164D58),onPrimaryContainer=Color(if(light)0xFF00333D else 0xFFB5ECF7),
            secondary=Color(if(light)0xFF42636B else 0xFFA6CBD4),secondaryContainer=Color(if(light)0xFFCCE7ED else 0xFF2A4850),onSecondaryContainer=Color(if(light)0xFF102F36 else 0xFFCCE7ED))
        "pink"->base.copy(primary=Color(if(light)0xFF96456B else 0xFFF4B1CF),onPrimary=Color(if(light)0xFFFFFFFF else 0xFF5B1838),
            primaryContainer=Color(if(light)0xFFFFD8E7 else 0xFF76304F),onPrimaryContainer=Color(if(light)0xFF3D0824 else 0xFFFFD8E7),
            secondary=Color(if(light)0xFF745360 else 0xFFDEBAC8),secondaryContainer=Color(if(light)0xFFF7DBE7 else 0xFF523D46),onSecondaryContainer=Color(if(light)0xFF2B1520 else 0xFFF7DBE7))
        else->base
    }
}
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

internal val LocalAppFontScale=staticCompositionLocalOf{1f}
internal val LocalAccent=staticCompositionLocalOf{"default"}
// Only text-container insets shrink; media, icons and touch targets keep their dp sizes.
@Composable internal fun textInset(value:Dp)=value*LocalAppFontScale.current.coerceIn(0.75f,1f)
@Composable internal fun textButtonPadding(horizontal:Dp=12.dp)=PaddingValues(horizontal=textInset(horizontal),vertical=textInset(8.dp))
@Composable internal fun appTextSize(value:TextUnit)=value*LocalAppFontScale.current

private fun appTypography(scale:Float):Typography {
    fun TextStyle.resized()=copy(fontSize=fontSize*scale,lineHeight=lineHeight*scale)
    val t=Typography()
    return t.copy(displayLarge=t.displayLarge.resized(),displayMedium=t.displayMedium.resized(),displaySmall=t.displaySmall.resized(),
        headlineLarge=t.headlineLarge.resized(),headlineMedium=t.headlineMedium.resized(),headlineSmall=t.headlineSmall.resized(),
        titleLarge=t.titleLarge.resized(),titleMedium=t.titleMedium.resized(),titleSmall=t.titleSmall.resized(),
        bodyLarge=t.bodyLarge.resized(),bodyMedium=t.bodyMedium.resized(),bodySmall=t.bodySmall.resized(),
        labelLarge=t.labelLarge.resized(),labelMedium=t.labelMedium.resized(),labelSmall=t.labelSmall.resized())
}

@Composable fun SakugappTheme(light:Boolean,fontScale:Float=1f,accent:String="default",content:@Composable ()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity
    val scale=fontScale.takeIf{it.isFinite()}?.coerceIn(0.75f,1.25f) ?: 1f
    val typography=remember(scale){appTypography(scale)}
    val colors=remember(light,accent){appColors(light,accent)}
    DisposableEffect(activity,light) {
        val style=if(light)SystemBarStyle.light(AndroidColor.TRANSPARENT,AndroidColor.TRANSPARENT)else SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
        onDispose { }
    }
    CompositionLocalProvider(LocalAppFontScale provides scale,LocalAccent provides accent,LocalMinimumInteractiveComponentSize provides 48.dp){
        MaterialTheme(colorScheme=colors,typography=typography,content=content)
    }
}

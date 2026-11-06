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
import androidx.compose.ui.graphics.toArgb
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
    return when(com.sakugapp.data.PreferenceRules.palette(accent)){
        "pink"->if(light) tintedColors(true,0xFFFFF5F8,0xFFFFFFFF,0xFFFCE6EE,0xFFAE416D,0xFFF4B6CC,0xFF432D39,0xFF785F6C,0xFFEED7E1,0xFFFFFFFF,0xFF9A7B89)
            else tintedColors(false,0xFF211820,0xFF332430,0xFF432D3C,0xFFE889B0,0xFF6C2C4B,0xFFFCEAF1,0xFFDAB9CA,0xFF674454,0xFF432D39,0xFFB78C9F)
        "mint"->if(light) tintedColors(true,0xFFF0FAF9,0xFFFFFFFF,0xFFDEF3EF,0xFF087B71,0xFFA7E3DC,0xFF243E3E,0xFF50716F,0xFFCDE6E2,0xFFFFFFFF,0xFF71938D)
            else tintedColors(false,0xFF142422,0xFF233633,0xFF2E4741,0xFF63C9BE,0xFF2E5F56,0xFFE3F6F2,0xFFB3D1CA,0xFF45655D,0xFF123C35,0xFF84AAA0)
        "amber"->if(light) tintedColors(true,0xFFFFF8EC,0xFFFFFDF8,0xFFFFE8BF,0xFF986000,0xFFFFC15A,0xFF332516,0xFF745E42,0xFFE5D3B6,0xFFFFFFFF,0xFF917A59)
            else tintedColors(false,0xFF18140F,0xFF211B14,0xFF342A1E,0xFFFFC45C,0xFF81551A,0xFFFFF0D9,0xFFD8BF9B,0xFF655338,0xFF352000,0xFFB49B76)
        else->base.copy(surfaceContainerLowest=base.background,surfaceContainerLow=base.surface,surfaceContainerHighest=base.surfaceContainerHigh,
            surfaceBright=base.surfaceContainerHigh,surfaceDim=base.background,surfaceTint=base.primary)
    }
}

/** Complete Material tokens: primary is slightly darker in light palettes for readable text buttons. */
private fun tintedColors(light:Boolean,background:Long,surface:Long,raised:Long,primary:Long,selected:Long,text:Long,secondaryText:Long,divider:Long,onPrimary:Long,outline:Long):ColorScheme {
    val b=Color(background);val s=Color(surface);val r=Color(raised);val p=Color(primary);val t=Color(text);val muted=Color(secondaryText);val choice=Color(selected)
    val base=if(light)lightColorScheme()else darkColorScheme()
    return base.copy(primary=p,onPrimary=Color(onPrimary),primaryContainer=choice,onPrimaryContainer=t,
        secondary=p,onSecondary=Color(onPrimary),secondaryContainer=choice,onSecondaryContainer=t,
        tertiary=p,onTertiary=Color(onPrimary),tertiaryContainer=r,onTertiaryContainer=t,
        background=b,onBackground=t,surface=s,onSurface=t,surfaceVariant=r,onSurfaceVariant=muted,
        surfaceContainerLowest=b,surfaceContainerLow=s,surfaceContainer=s,surfaceContainerHigh=r,surfaceContainerHighest=r,
        surfaceBright=s,surfaceDim=b,surfaceTint=p,outline=Color(outline),outlineVariant=Color(divider),
        inverseSurface=t,inverseOnSurface=b,inversePrimary=if(light)choice else Color(onPrimary))
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
    DisposableEffect(activity,light,colors.background) {
        val style=if(light)SystemBarStyle.light(colors.background.toArgb(),colors.background.toArgb())else SystemBarStyle.dark(colors.background.toArgb())
        activity?.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
        onDispose { }
    }
    CompositionLocalProvider(LocalAppFontScale provides scale,LocalAccent provides accent,LocalMinimumInteractiveComponentSize provides 48.dp){
        MaterialTheme(colorScheme=colors,typography=typography,content=content)
    }
}

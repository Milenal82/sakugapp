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
    primary=Color(0xFF4655A6),background=Color(0xFFF8F9FC),surface=Color(0xFFFFFFFF),
) else darkColorScheme(primary=Color(0xFFB9C3FF),background=Color(0xFF101114),surface=Color(0xFF191B22))

internal fun copyrightColor(light:Boolean)=if(light)Color(0xFF806000)else Color(0xFFFFD54F)
internal fun artistColor(light:Boolean)=if(light)Color(0xFF7536A5)else Color(0xFFCE93D8)

@Composable fun SakugappTheme(light:Boolean,content:@Composable ()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity
    DisposableEffect(activity,light) {
        val style=if(light)SystemBarStyle.light(AndroidColor.TRANSPARENT,AndroidColor.TRANSPARENT)else SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
        onDispose { }
    }
    MaterialTheme(colorScheme=appColors(light),content=content)
}

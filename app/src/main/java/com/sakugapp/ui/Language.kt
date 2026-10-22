// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
val LocalLanguage = staticCompositionLocalOf { "en" }
@Composable fun text(en: String, ja: String): String = when(LocalLanguage.current) {
    "ja" -> ja
    "zh-TW" -> TraditionalChinese.get(en)
    else -> en
}
@Composable fun translateMessage(en:String):String = if(LocalLanguage.current == "zh-TW")TraditionalChinese.get(en)else en

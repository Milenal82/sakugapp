// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
val LocalLanguage = staticCompositionLocalOf { "en" }
@Composable fun text(en: String, ja: String): String = if (LocalLanguage.current == "ja") ja else en

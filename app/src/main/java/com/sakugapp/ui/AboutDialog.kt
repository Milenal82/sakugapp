// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutDialog(dismiss: () -> Unit) {
    val context = LocalContext.current
    var document by remember { mutableStateOf("THIRD-PARTY-NOTICES.md") }
    val text by produceState("正在读取许可…", document) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$document").bufferedReader().use { it.readText() } }
                .getOrDefault("无法读取许可文件，请在项目源码的 LICENSE 和 docs/third-party-notices.md 中查看。")
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Sakugapp 0.2.0") },
        text = {
            Column {
                Text("Sakugapp contributors · GPL-3.0-only\n本应用不提供任何保证，允许按许可证修改和再分发。")
                Row {
                    TextButton(onClick = { document = "THIRD-PARTY-NOTICES.md" }) { Text("来源与声明") }
                    TextButton(onClick = { document = "GPL-3.0.txt" }) { Text("GPL 全文") }
                }
                Text(text, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } },
    )
}

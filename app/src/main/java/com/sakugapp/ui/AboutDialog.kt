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
    val licenseText by produceState("Loading license…", document) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$document").bufferedReader().use { it.readText() } }
                .getOrDefault("Unable to read the license. See LICENSE and docs/third-party-notices.md in the source project.")
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Sakugapp 0.5.0") },
        text = {
            Column {
                Text(text("Sakugapp contributors · GPL-3.0-only\nNo warranty. Modification and redistribution are permitted under the license.","Sakugapp contributors · GPL-3.0-only\n無保証です。ライセンスに従った改変・再配布が可能です。"))
                Row {
                    TextButton(onClick = { document = "THIRD-PARTY-NOTICES.md" }) { Text(text("Notices","出典と通知")) }
                    TextButton(onClick = { document = "GPL-3.0.txt" }) { Text(text("GPL license","GPL本文")) }
                }
                Text(licenseText, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(text("Close","閉じる")) } },
    )
}

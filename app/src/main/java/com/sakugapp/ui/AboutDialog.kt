// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sakugapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutDialog(dismiss: () -> Unit) {
    val context = LocalContext.current
    var document by remember { mutableStateOf("THIRD-PARTY-NOTICES.md") }
    val licenseText by produceState(context.getString(R.string.ui_loading_license), document) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$document").bufferedReader().use { it.readText() } }
                .getOrDefault(context.getString(R.string.ui_unable_to_read_the_license_see_license_and_docs_third_party_notic))
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(text(R.string.ui_app_version,BuildConfig.VERSION_NAME)) },
        text = {
            Column {
                Text(text(R.string.ui_sakugapp_contributors_gpl_3_0_only_no_warranty_modification_and_r))
                Row {
                    TextButton(onClick = { document = "THIRD-PARTY-NOTICES.md" }) { Text(text(R.string.ui_notices)) }
                    TextButton(onClick = { document = "GPL-3.0.txt" }) { Text(text(R.string.ui_gpl_license)) }
                }
                Text(licenseText, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(text(R.string.ui_close)) } },
    )
}

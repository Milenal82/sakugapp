// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Wrapping labels and a visible check keep tools usable with long translations. */
@Composable internal fun SearchToolTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
) {
    val active = selected == true
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(max = 360.dp).heightIn(min = 48.dp).semantics {
            role = Role.Button
            selected?.let { this.selected = it }
        },
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .10f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
            if (active) Text("✓", style = MaterialTheme.typography.labelLarge)
        }
    }
}

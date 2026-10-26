// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Shared 48 dp touch target, 24 dp glyph and accessible long-press tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ActionIcon(@DrawableRes icon:Int,label:String,onClick:()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,selected:Boolean=false){
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip={PlainTooltip{Text(label)}},state=rememberTooltipState()){
        IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp)){
            Icon(painterResource(icon),contentDescription=label,modifier=Modifier.size(24.dp),
                tint=if(!enabled)MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f)
                else if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

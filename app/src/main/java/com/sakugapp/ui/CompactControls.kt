// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.annotation.DrawableRes
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

internal object FeatureStyle {
    val shape = RoundedCornerShape(16.dp)
    val gap = 10.dp
    val inset = 12.dp
}

/** One surface for home tiles, compact tools and selected scopes. Text may grow vertically. */
@Composable internal fun FeatureSurface(
    modifier:Modifier=Modifier, onClick:(()->Unit)?=null, enabled:Boolean=true,
    selected:Boolean?=null, content:@Composable ()->Unit,
) {
    val color=if(selected==true)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val shape=FeatureStyle.shape
    if(onClick==null)Surface(modifier,shape=shape,color=color,content=content)
    else Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp).semantics{
        role=Role.Button;selected?.let{this.selected=it}
    },shape=shape,color=color,contentColor=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f),content=content)
}

@Composable internal fun HomeTile(@DrawableRes icon:Int,title:String,subtitle:String,modifier:Modifier=Modifier,click:()->Unit){
    FeatureSurface(modifier.heightIn(min=96.dp),onClick=click){
        Column(Modifier.padding(textInset(FeatureStyle.inset)),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Icon(painterResource(icon),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.titleSmall)
            if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
    FeatureSurface(modifier.widthIn(max=360.dp).width(IntrinsicSize.Max),onClick,enabled,selected) {
        Row(Modifier.padding(horizontal = textInset(12.dp), vertical = textInset(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (active) Text("✓", style = MaterialTheme.typography.labelLarge)
        }
    }
}

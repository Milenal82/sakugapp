// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sakugapp.R

/** One popup surface, consistent rows and a checked selection; no nested cards. */
@Composable internal fun TileMenu(expanded:Boolean,onDismissRequest:()->Unit,content:@Composable ColumnScope.()->Unit){
    DropdownMenu(expanded=expanded,onDismissRequest=onDismissRequest,shape=RoundedCornerShape(16.dp),
        containerColor=MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation=0.dp,shadowElevation=4.dp,
        modifier=Modifier.widthIn(max=320.dp),content=content)
}

@Composable internal fun AppMenuItem(text:@Composable ()->Unit,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    @DrawableRes icon:Int?=R.drawable.ic_tune,selected:Boolean=false,destructive:Boolean=false,toned:Boolean=false){
    val colors=MaterialTheme.colorScheme;val checked=selected
    val foreground=if(destructive)colors.error else colors.onSurface
    DropdownMenuItem(text={ProvideTextStyle(MaterialTheme.typography.bodyMedium,text)},onClick=onClick,enabled=enabled,
        leadingIcon=icon?.let{value->{Icon(painterResource(value),null,Modifier.size(20.dp))}},
        trailingIcon=if(selected)({Icon(painterResource(R.drawable.ic_check),null,Modifier.size(18.dp))})else null,
        modifier=modifier.padding(horizontal=4.dp,vertical=1.dp).clip(RoundedCornerShape(10.dp))
            .background(when{selected->colors.secondaryContainer;toned->colors.surfaceContainerHigh;else->Color.Transparent})
            .heightIn(min=48.dp).semantics{this.selected=checked},
        contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),
        colors=MenuDefaults.itemColors(textColor=foreground,leadingIconColor=foreground,trailingIconColor=foreground))
}

@Composable internal fun MenuDivider(){HorizontalDivider(Modifier.padding(horizontal=12.dp,vertical=4.dp),color=MaterialTheme.colorScheme.outlineVariant)}

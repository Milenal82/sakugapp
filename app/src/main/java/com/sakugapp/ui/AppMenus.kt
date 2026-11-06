// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sakugapp.R

/** One popup surface, consistent rows and a checked selection; no nested cards. */
@Composable internal fun TileMenu(expanded:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    DropdownMenu(expanded=expanded,onDismissRequest=onDismissRequest,shape=RoundedCornerShape(16.dp),
        containerColor=MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation=0.dp,shadowElevation=4.dp,
        modifier=modifier.widthIn(max=320.dp),content=content)
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

/** The global menu deliberately exposes six destinations; secondary tools stay on their pages. */
@Composable internal fun MainOverflowMenu(expanded:Boolean,current:String?,dismiss:()->Unit,navigate:(String)->Unit,legal:()->Unit){
    TileMenu(expanded,dismiss,Modifier.widthIn(max=280.dp).testTag("main-overflow")){
        val entries=listOf(
            Triple("posts",R.drawable.ic_book,R.string.ui_menu_posts),
            Triple("search_home",R.drawable.ic_search,R.string.ui_menu_search),
            Triple("account",R.drawable.ic_profile,R.string.ui_menu_account),
            Triple("library",R.drawable.ic_folder,R.string.ui_menu_library),
            Triple("settings",R.drawable.ic_tune,R.string.ui_menu_settings))
        entries.forEach{(route,icon,label)->AppMenuItem(icon=icon,selected=current==route || (route=="posts" && current=="post/{id}"),text={Text(text(label))},modifier=Modifier.testTag("menu-$route"),onClick={dismiss();navigate(route)})}
        MenuDivider()
        AppMenuItem(icon=R.drawable.ic_book,text={Text(text(R.string.ui_menu_legal))},modifier=Modifier.testTag("menu-legal"),onClick={dismiss();legal()})
    }
}

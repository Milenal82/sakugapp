// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.Tag

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun OriginalTag(tag:Tag,color:Color,search:(String)->Unit,
    modifier:Modifier=Modifier,fontSize:TextUnit=14.sp,maxLines:Int=Int.MAX_VALUE,showNote:Boolean=false,previewSpacing:Float?=null){
    val app=LocalTagActions.current;var options by remember(tag.name){mutableStateOf(false)}
    val notes=if(app!=null && showNote)app.tagNotes.collectAsStateWithLifecycle().value else emptyList()
    val note=notes.firstOrNull{it.name==tag.name}?.note
    val action=if(app==null)Modifier.clickable{search(tag.name)}else Modifier.combinedClickable(onClick={if(tag.category==com.sakugapp.data.TagCategory.ARTIST)options=true else search(tag.name)},onLongClick={options=true},onLongClickLabel=text(R.string.ui_tag_options))
    val base=LocalTextStyle.current;val density=LocalDensity.current;val textSize=appTextSize(fontSize)
    val style=remember(base,density,textSize,previewSpacing){if(previewSpacing==null)base else base.copy(
        lineHeight=(textSize.value*1.2f+with(density){previewSpacing.dp.toSp().value}).sp,
        lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.Both))}
    if(previewSpacing!=null && !showNote){
        Text(tag.name,color=color,fontSize=textSize,style=style,maxLines=maxLines,overflow=TextOverflow.Ellipsis,modifier=modifier.then(action).testTag("tag-${tag.name}"))
    }else Column(modifier.then(action).padding(vertical=if(previewSpacing==null)3.dp else 0.dp)){
        Text(tag.name,color=color,fontSize=textSize,style=style,maxLines=maxLines,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("tag-${tag.name}"))
        if(!note.isNullOrBlank())Text(note,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
    if(options && app!=null)TagActionsDialog(tag,app){options=false}
}

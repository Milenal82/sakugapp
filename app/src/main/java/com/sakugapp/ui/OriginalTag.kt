// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.Tag

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun OriginalTag(tag:Tag,color:Color,search:(String)->Unit,
    modifier:Modifier=Modifier,fontSize:TextUnit=14.sp,maxLines:Int=Int.MAX_VALUE,showNote:Boolean=false){
    val app=LocalTagActions.current;var options by remember(tag.name){mutableStateOf(false)}
    val notes=if(app!=null && showNote)app.tagNotes.collectAsStateWithLifecycle().value else emptyList()
    val note=notes.firstOrNull{it.name==tag.name}?.note
    val action=if(app==null)Modifier.clickable{search(tag.name)}else Modifier.combinedClickable(onClick={search(tag.name)},onLongClick={options=true},onLongClickLabel=text(R.string.ui_tag_options))
    Column(modifier.then(action).padding(vertical=3.dp)){
        Text(tag.name,color=color,fontSize=appTextSize(fontSize),maxLines=maxLines,overflow=TextOverflow.Ellipsis,modifier=Modifier)
        if(!note.isNullOrBlank())Text(note,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
    if(options && app!=null)TagActionsDialog(tag,app){options=false}
}

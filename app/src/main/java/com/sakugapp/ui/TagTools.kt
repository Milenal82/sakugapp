// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

internal val LocalTagActions=staticCompositionLocalOf<AppViewModel?>{null}
internal fun tagColor(tag:Tag,light:Boolean)=when(tag.category){TagCategory.ARTIST->artistColor(light);TagCategory.COPYRIGHT->copyrightColor(light);else->null}

@Composable internal fun TagActionsDialog(tag:Tag,app:AppViewModel,dismiss:()->Unit){
    val saved by app.savedTags.collectAsStateWithLifecycle();val notes by app.tagNotes.collectAsStateWithLifecycle()
    val present=saved.any{it.name==tag.name};val current=notes.firstOrNull{it.name==tag.name}?.note.orEmpty()
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var editing by rememberSaveable{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun operate(action:suspend()->Unit){scope.launch{try{action();error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}}
    if(editing)TagNoteDialog(tag.name,current,app,{editing=false})else AlertDialog(onDismissRequest=dismiss,
        title={Text(tag.name)},text={Column(Modifier.verticalScroll(rememberScrollState())){
            if(current.isNotBlank())Text(current,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("tag-note-text"))
            TextButton(onClick={operate{if(present)app.personal.removeTag(tag.name)else app.personal.saveTag(tag)}},modifier=Modifier.testTag("toggle-tag-favorite")){Text(text(if(present)R.string.ui_unfavorite_tag else R.string.ui_favorite_tag))}
            TextButton(onClick={editing=true},modifier=Modifier.testTag("edit-tag-note")){Text(text(if(current.isBlank())R.string.ui_add_tag_note else R.string.ui_edit_tag_note))}
            if(current.isNotBlank())TextButton(onClick={operate{app.personal.note(tag.name,"")}},modifier=Modifier.testTag("clear-tag-note")){Text(text(R.string.ui_clear_tag_note))}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(onClick=dismiss){Text(text(R.string.ui_close))}})
}
@Composable private fun TagNoteDialog(name:String,current:String,app:AppViewModel,dismiss:()->Unit){
    var draft by rememberSaveable(name){mutableStateOf(current)};var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val valid=runCatching{PersonalRules.note(draft)}.isSuccess
    AlertDialog(onDismissRequest=dismiss,title={Text(text(R.string.ui_edit_tag_note))},text={Column{
        Text(name,style=MaterialTheme.typography.labelLarge)
        OutlinedTextField(draft,{draft=it},label={Text(text(R.string.ui_tag_note))},minLines=2,maxLines=6,
            modifier=Modifier.fillMaxWidth().testTag("tag-note-input"),isError=!valid,
            supportingText={Text(text(R.string.ui_note_limit,PersonalRules.NOTE_LENGTH))})
        if(!valid)Text(text(R.string.ui_note_length_error),color=MaterialTheme.colorScheme.error)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=valid,onClick={scope.launch{try{app.personal.note(name,draft);dismiss()}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}},modifier=Modifier.testTag("save-tag-note")){Text(text(R.string.ui_save))}},dismissButton={TextButton(onClick=dismiss){Text(text(R.string.ui_cancel))}})
}

/** The results toolbar saves literal tags, never a compound query or its metadata operators. */
@Composable internal fun SearchTagFavorite(query:String,posts:List<Post>,app:AppViewModel){
    val tags=remember(query,posts){PersonalRules.queryTags(query,posts)}
    var choosing by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<Tag?>(null)}
    val saved by app.savedTags.collectAsStateWithLifecycle()
    val single=tags.singleOrNull();val present=single!=null && saved.any{it.name==single.name}
    ActionIcon(if(present)R.drawable.ic_favorite_filled else R.drawable.ic_favorite,text(R.string.ui_tag_favorites),{if(single!=null)selected=single else choosing=true},Modifier.testTag("search-tag-favorite"),selected=present)
    if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text(text(R.string.ui_tag_favorites))},text={Column(Modifier.verticalScroll(rememberScrollState())){
        if(tags.isEmpty())Text(text(R.string.ui_literal_tags_only))
        tags.forEach{tag->TextButton(onClick={choosing=false;selected=tag}){Text(tag.name)}}
    }},confirmButton={TextButton(onClick={choosing=false}){Text(text(R.string.ui_close))}})
    selected?.let{TagActionsDialog(it,app){selected=null}}
}

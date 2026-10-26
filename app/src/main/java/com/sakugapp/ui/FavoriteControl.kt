// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

internal fun folderName(folder:FavoriteFolder,context:android.content.Context)=if(folder.id==1L)context.getString(R.string.ui_default_folder)else folder.name
@Composable fun FavoriteControl(post:Post,app:AppViewModel){
    val state by app.library.collectAsStateWithLifecycle();val scope=rememberCoroutineScope();val context=LocalContext.current
    var manage by remember(post.id){mutableStateOf(false)};var error by remember(post.id){mutableStateOf<String?>(null)}
    var name by remember(post.id){mutableStateOf("")}
    fun operation(action:suspend()->Unit){scope.launch{try{action();error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}}
    Column{
        Row{
            FilledTonalButton(onClick={if(state.contains(post.id))manage=true else operation{app.localLibrary.membership(post,1,true)}},modifier=Modifier.testTag("quick-favorite")){Text(text(if(state.contains(post.id))R.string.ui_saved_local else R.string.ui_quick_favorite))}
            TextButton(onClick={manage=true},modifier=Modifier.testTag("favorite-folders")){Text(text(R.string.ui_manage_folders))}
        }
        if(state.contains(post.id))Text(text(R.string.ui_saved_local),style=MaterialTheme.typography.labelSmall)
        if(state.error)Text(text(R.string.ui_library_failed),color=MaterialTheme.colorScheme.error)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    PacedAlertDialog(visible=manage,onDismissRequest={manage=false},title={Text(text(R.string.ui_manage_folders))},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){
        state.folders.forEach{folder->Row{
            Checkbox(checked=state.contains(post.id,folder.id),onCheckedChange={checked->operation{app.localLibrary.membership(post,folder.id,checked)}})
            TextButton(onClick={operation{app.localLibrary.membership(post,folder.id,!state.contains(post.id,folder.id))}}){Text(folderName(folder,context))}
        }}
        OutlinedTextField(name,{name=it.take(80)},label={Text(text(R.string.ui_folder_name))})
        TextButton(enabled=name.isNotBlank(),onClick={operation{val id=app.localLibrary.createFolder(name);app.localLibrary.membership(post,id,true);name=""}}){Text(text(R.string.ui_new_folder))}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={manage=false}){Text(text(R.string.ui_close))}})
}

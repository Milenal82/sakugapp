// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Both entry points use the same picker, validation, preview and transactional import. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun BackupActions(app:AppViewModel){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var preview by remember{mutableStateOf<BackupPreview?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var folders by remember{mutableStateOf(FolderConflictPolicy.MERGE_BY_NAME)}
    var notes by remember{mutableStateOf(NoteConflictPolicy.KEEP_EXISTING)}
    fun operation(importing:Boolean=false,action:suspend()->Unit){scope.launch{
        busy=true;message=null
        try{action()}catch(e:CancellationException){throw e}catch(e:Exception){
            if(importing)preview=null
            message=(if(importing)context.getString(R.string.ui_backup_result,0,0,1)+"\n" else "")+libraryError(context,e)
        }finally{busy=false}
    }}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)operation{
        val body=app.localLibrary.export()
        withContext(Dispatchers.IO){checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use{it.write(body)}}
        message=context.getString(R.string.ui_exported)
    }}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)operation(importing=true){
        val body=withContext(Dispatchers.IO){checkNotNull(context.contentResolver.openInputStream(uri)).use{input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var size=0
            while(true){val count=input.read(buffer);if(count<0)break;size+=count;if(size>LibraryCodec.MAX_BYTES)throw LibraryFailure("library_limit");output.write(buffer,0,count)}
            try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()}
            catch(_:java.nio.charset.CharacterCodingException){throw LibraryFailure("invalid_backup")}
        }}
        preview=withContext(Dispatchers.Default){app.localLibrary.preview(body)}
        folders=FolderConflictPolicy.MERGE_BY_NAME;notes=NoteConflictPolicy.KEEP_EXISTING
    }}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            OutlinedButton(contentPadding=textButtonPadding(24.dp),enabled=!busy,onClick={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.testTag("import-favorites")){
                Icon(painterResource(R.drawable.ic_download),null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(text(R.string.ui_import_library))
            }
            OutlinedButton(contentPadding=textButtonPadding(24.dp),enabled=!busy,onClick={exporter.launch("sakugapp-favorites.json")},modifier=Modifier.testTag("export-favorites")){
                Icon(painterResource(R.drawable.ic_upload),null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(text(R.string.ui_export_library))
            }
        }
        Text(text(R.string.ui_import_warning),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("backup-result"))}
    }
    preview?.let{value->AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text(text(R.string.ui_import_library))},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            val c=value.counts
            Text(text(R.string.ui_backup_preview_counts,c.folders,c.posts,c.relationships,c.tags,c.notes),Modifier.testTag("backup-preview-counts"))
            Text(text(R.string.ui_import_warning),style=MaterialTheme.typography.bodySmall)
            if(value.folderConflicts>0){
                HorizontalDivider();Text(text(R.string.ui_backup_folder_conflicts,value.folderConflicts))
                BackupChoice(text(R.string.ui_backup_merge_folders),folders==FolderConflictPolicy.MERGE_BY_NAME){folders=FolderConflictPolicy.MERGE_BY_NAME}
                BackupChoice(text(R.string.ui_backup_keep_folders),folders==FolderConflictPolicy.KEEP_BOTH){folders=FolderConflictPolicy.KEEP_BOTH}
            }
            if(value.noteConflicts>0){
                HorizontalDivider();Text(text(R.string.ui_backup_note_conflicts,value.noteConflicts))
                BackupChoice(text(R.string.ui_backup_keep_notes),notes==NoteConflictPolicy.KEEP_EXISTING){notes=NoteConflictPolicy.KEEP_EXISTING}
                BackupChoice(text(R.string.ui_backup_replace_notes),notes==NoteConflictPolicy.REPLACE){notes=NoteConflictPolicy.REPLACE}
            }
        }
    },confirmButton={TextButton(contentPadding=textButtonPadding(),enabled=!busy,onClick={operation(importing=true){
        val result=app.localLibrary.import(value,folders,notes);preview=null
        message=context.getString(R.string.ui_backup_result,result.added,result.skipped,result.failed)
    }},modifier=Modifier.testTag("confirm-import-favorites")){Text(text(R.string.ui_merge))}},dismissButton={TextButton(contentPadding=textButtonPadding(),enabled=!busy,onClick={preview=null}){Text(text(R.string.ui_cancel))}})}
}

@Composable private fun BackupChoice(label:String,selected:Boolean,choose:()->Unit){
    Row(Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick=choose).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        RadioButton(selected=selected,onClick=null);Spacer(Modifier.width(6.dp));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
    }
}

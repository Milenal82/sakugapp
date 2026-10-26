// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LibraryScreen(app:AppViewModel,open:(Post)->Unit){
    val state by app.library.collectAsStateWithLifecycle();val context=LocalContext.current;val scope=rememberCoroutineScope()
    var folderId by rememberSaveable{mutableStateOf<Long?>(null)}
    var query by rememberSaveable{mutableStateOf("")};var sort by rememberSaveable{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf(setOf<Long>())}
    var folderDialog by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
    var deleting by remember{mutableStateOf(false)};var removing by remember{mutableStateOf(false)};var moving by remember{mutableStateOf(false)}
    var importBody by remember{mutableStateOf<String?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)};var downloadConfirm by remember{mutableStateOf(false)}
    var job by remember{mutableStateOf<Job?>(null)};var done by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(0)}
    var usage by remember{mutableLongStateOf(app.offlineMedia.bytes())};var clearMedia by remember{mutableStateOf(false)}
    val folder=state.folders.firstOrNull{it.id==folderId}
    fun operation(action:suspend()->Unit){scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){message=libraryError(context,e)}}}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)operation{
        val body=app.localLibrary.export()
        withContext(Dispatchers.IO){checkNotNull(context.contentResolver.openOutputStream(uri,"w")).bufferedWriter().use{it.write(body)}}
        message=context.getString(R.string.ui_exported)
    }}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)operation{
        val body=withContext(Dispatchers.IO){checkNotNull(context.contentResolver.openInputStream(uri)).use{input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var bytes=0
            while(true){val n=input.read(buffer);if(n<0)break;bytes+=n;if(bytes>LibraryCodec.MAX_BYTES)throw LibraryFailure("library_limit");output.write(buffer,0,n)}
            output.toString("UTF-8")
        }}
        withContext(Dispatchers.Default){LibraryCodec.decode(body)};importBody=body
    }}
    val candidates=remember(state,folderId,query,sort){
        val time=state.links.filter{folderId==null || it.folderId==folderId}.groupBy{it.postId}.mapValues{e->e.value.minOf{it.addedAt}}
        val filtered=state.inFolder(folderId).filter{p->query.isBlank() || p.id.toString().contains(query) || state.decoded[p.id]?.tags.orEmpty().any{it.name.contains(query,true) || state.translations[it.name]?.contains(query,true)==true}}
        when(sort){1->filtered.sortedBy{time[it.id] ?: it.savedAt};2->filtered.sortedByDescending{it.id};3->filtered.sortedBy{it.id};else->filtered.sortedByDescending{time[it.id] ?: it.savedAt}}
    }
    LaunchedEffect(folderId){selected=emptySet()}
    LazyColumn(Modifier.fillMaxSize().testTag("local-library"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            Text(if(folder==null)text(R.string.ui_all_favorites)else folderName(folder,context),style=MaterialTheme.typography.titleLarge)
            Text(text(R.string.ui_library_snapshot_hint),style=MaterialTheme.typography.bodySmall)
            if(state.error)Text(text(R.string.ui_library_failed),color=MaterialTheme.colorScheme.error)
            FlowRow{
                TextButton(onClick={folderId=null}){Text(text(R.string.ui_all_favorites))}
                TextButton(onClick={name="";rename=false;folderDialog=true}){Text(text(R.string.ui_new_folder))}
                TextButton(onClick={exporter.launch("sakugapp-favorites.json")}){Text(text(R.string.ui_export_library))}
                TextButton(onClick={importer.launch(arrayOf("application/json","text/plain"))}){Text(text(R.string.ui_import_library))}
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
        }
        if(folderId==null)items(state.folders,key={"folder-${it.id}"}){f->
            val entries=state.inFolder(f.id);val ids=entries.map{it.id}.toSet()
            val cover=state.decoded[f.coverPostId?.takeIf{it in ids} ?: entries.firstOrNull()?.id]
            Card(Modifier.fillMaxWidth().clickable{folderId=f.id}){Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                AsyncImage(cover?.previewUrl,folderName(f,context),Modifier.width(72.dp).height(48.dp),contentScale=ContentScale.Fit)
                Column{Text(folderName(f,context));Text(text(R.string.ui_posts_count,entries.size),style=MaterialTheme.typography.bodySmall)}
            }}
        }
        item{
            if(folder!=null)FlowRow{
                if(folder.id!=1L){
                    TextButton(onClick={name=folder.name;rename=true;folderDialog=true}){Text(text(R.string.ui_rename))}
                    TextButton(onClick={deleting=true}){Text(text(R.string.ui_delete_folder))}
                }
                TextButton(onClick={operation{app.localLibrary.reorder(folder.id,-1)}}){Text(text(R.string.ui_move_up))}
                TextButton(onClick={operation{app.localLibrary.reorder(folder.id,1)}}){Text(text(R.string.ui_move_down))}
                TextButton(enabled=selected.size==1,onClick={operation{app.localLibrary.cover(folder.id,selected.single())}}){Text(text(R.string.ui_set_cover))}
                TextButton(enabled=!busy && candidates.any{state.decoded[it.id]?.let{p->p.isVideo && app.offlineMedia.existing(p)==null}==true},onClick={downloadConfirm=true}){Text(text(R.string.ui_batch_download))}
            }
            OutlinedTextField(query,{query=it},label={Text(text(R.string.ui_library_search))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("library-search"))
            FlowRow{for((index,label)in listOf(R.string.ui_sort_saved_desc,R.string.ui_sort_saved_asc,R.string.ui_sort_id_desc,R.string.ui_sort_id_asc).withIndex())FilterChip(selected=sort==index,onClick={sort=index},label={Text(text(label))})}
            if(selected.isNotEmpty())FlowRow{
                Text(text(R.string.ui_selected_count,selected.size))
                TextButton(onClick={moving=true}){Text(text(R.string.ui_move_selected))}
                TextButton(onClick={removing=true}){Text(text(R.string.ui_remove_selected))}
                TextButton(onClick={selected=emptySet()}){Text(text(R.string.ui_cancel))}
            }
            if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text(text(R.string.ui_download_batch_progress,done,total));TextButton(onClick={job?.cancel()}){Text(text(R.string.ui_cancel_download))}}
            if(candidates.isEmpty())Text(text(R.string.ui_library_empty))
        }
        items(candidates,key={"post-${it.id}"}){entry->state.decoded[entry.id]?.let{post->
            Card{Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Checkbox(checked=post.id in selected,onCheckedChange={selected=if(it)selected+post.id else selected-post.id},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_select_post,post.id)})
                AsyncImage(post.previewUrl,text(R.string.ui_post_description,post.id),Modifier.width(80.dp).height(56.dp).clickable{open(post)},contentScale=ContentScale.Fit)
                Column(Modifier.weight(1f).clickable{open(post)}){
                    Text("#${post.id}");Text(post.tags.take(3).joinToString(" "){it.name},style=MaterialTheme.typography.bodySmall)
                    Text(text(R.string.ui_score_value,post.score?.toString() ?: "?"),color=scoreColor(MaterialTheme.colorScheme.background.luminance()>0.5f))
                    if(app.offlineMedia.existing(post)!=null)Text(text(R.string.ui_offline_ready),style=MaterialTheme.typography.labelSmall)
                }
            }}
        }}
        item{
            Text(text(R.string.ui_offline_usage,usage/(1024*1024)),style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy,onClick={clearMedia=true}){Text(text(R.string.ui_clear_offline_media))}
        }
    }
    if(folderDialog)AlertDialog(onDismissRequest={folderDialog=false},title={Text(text(if(rename)R.string.ui_rename else R.string.ui_new_folder))},text={OutlinedTextField(name,{name=it.take(80)},label={Text(text(R.string.ui_folder_name))})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={operation{if(rename && folder!=null)app.localLibrary.rename(folder.id,name)else app.localLibrary.createFolder(name);folderDialog=false}}){Text(text(if(rename)R.string.ui_rename else R.string.ui_create))}},dismissButton={TextButton(onClick={folderDialog=false}){Text(text(R.string.ui_cancel))}})
    if(deleting && folder!=null)AlertDialog(onDismissRequest={deleting=false},title={Text(text(R.string.ui_delete_folder))},text={Text(text(R.string.ui_relationship_delete_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.deleteFolder(folder.id);folderId=null;deleting=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={deleting=false}){Text(text(R.string.ui_cancel))}})
    if(removing)AlertDialog(onDismissRequest={removing=false},title={Text(text(R.string.ui_remove_selected))},text={Text(text(R.string.ui_relationship_delete_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.batch(selected,folderId,null);selected=emptySet();removing=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={removing=false}){Text(text(R.string.ui_cancel))}})
    if(moving)AlertDialog(onDismissRequest={moving=false},title={Text(text(R.string.ui_move_selected))},text={Column{
        Text(text(R.string.ui_move_explanation))
        state.folders.forEach{target->TextButton(onClick={operation{app.localLibrary.batch(selected,folderId,target.id);selected=emptySet();moving=false}}){Text(folderName(target,context))}}
    }},confirmButton={TextButton(onClick={moving=false}){Text(text(R.string.ui_cancel))}})
    importBody?.let{body->AlertDialog(onDismissRequest={importBody=null},title={Text(text(R.string.ui_import_library))},text={Text(text(R.string.ui_import_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.import(body);importBody=null;message=context.getString(R.string.ui_imported)}}){Text(text(R.string.ui_merge))}},dismissButton={TextButton(onClick={importBody=null}){Text(text(R.string.ui_cancel))}})}
    if(clearMedia)AlertDialog(onDismissRequest={clearMedia=false},title={Text(text(R.string.ui_clear_offline_media))},text={Text(text(R.string.ui_clear_offline_warning))},confirmButton={TextButton(onClick={operation{app.offlineMedia.clear();usage=0;clearMedia=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={clearMedia=false}){Text(text(R.string.ui_cancel))}})
    if(downloadConfirm)AlertDialog(onDismissRequest={downloadConfirm=false},title={Text(text(R.string.ui_batch_download))},text={Text(text(R.string.ui_batch_download_hint))},confirmButton={TextButton(onClick={
        downloadConfirm=false;val pending=state.inFolder(folderId).mapNotNull{state.decoded[it.id]}.filter{it.isVideo && app.offlineMedia.existing(it)==null}.take(OfflineLibraryMedia.BATCH_SIZE)
        busy=true;done=0;total=pending.size
        job=scope.launch{try{for(post in pending){app.offlineMedia.download(post);done++};message=context.getString(R.string.ui_video_saved)}catch(e:CancellationException){message=context.getString(R.string.ui_download_cancelled);throw e}catch(e:Exception){message=libraryError(context,e)}finally{busy=false;usage=app.offlineMedia.bytes()}}
    }){Text(text(R.string.ui_download_video))}},dismissButton={TextButton(onClick={downloadConfirm=false}){Text(text(R.string.ui_cancel))}})
}

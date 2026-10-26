// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.sakugapp.PostCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
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
@Composable fun LibraryScreen(app:AppViewModel,search:(String)->Unit={},open:(Post,List<Post>)->Unit){
    val state by app.library.collectAsStateWithLifecycle();val context=LocalContext.current;val scope=rememberCoroutineScope()
    var folderId by rememberSaveable{mutableStateOf<Long?>(null)}
    val preferences by app.settings.collectAsStateWithLifecycle();val sort=preferences.librarySort
    var selected by remember{mutableStateOf(setOf<Long>())}
    var folderDialog by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
    var deleting by remember{mutableStateOf(false)};var removing by remember{mutableStateOf(false)};var moving by remember{mutableStateOf(false)}
    var importBody by remember{mutableStateOf<String?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)};var downloadConfirm by remember{mutableStateOf(false)}
    var job by remember{mutableStateOf<Job?>(null)};var done by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(0)};var currentBytes by remember{mutableLongStateOf(0)};var currentSize by remember{mutableLongStateOf(-1L)}
    var usage by remember{mutableLongStateOf(app.offlineMedia.bytes())};var clearMedia by remember{mutableStateOf(false)}
    val folder=state.folders.firstOrNull{it.id==folderId}
    val inside=folderId!=null
    val actualId=folderId?.takeIf{it>0}
    val title=if(folder==null)text(R.string.ui_all_favorites)else folderName(folder,context)
    val settings=preferences.copy(lightTheme=themeIsLight(preferences.themeMode,isSystemInDarkTheme()),columns=GridPolicy.effective(preferences.columns,LocalConfiguration.current.smallestScreenWidthDp))
    val metered by remember(context){meteredNetwork(context.applicationContext)}.collectAsStateWithLifecycle(initialValue=true)
    var more by remember{mutableStateOf(false)}
    var manage by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<Long?>(null)}
    val grid=rememberLazyGridState()
    var gridFolder by rememberSaveable{mutableStateOf<Long?>(null)}
    BackHandler(enabled=inside){folderId=null}
    LaunchedEffect(folderId,state.folders){if(folderId!=null && folderId!=0L && state.folders.isNotEmpty() && folder==null)folderId=null}

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
    val candidates=remember(state,folderId,sort){
        val time=state.links.filter{actualId==null || it.folderId==actualId}.groupBy{it.postId}.mapValues{e->e.value.minOf{it.addedAt}}
        val filtered=state.inFolder(actualId)
        when(sort){1->filtered.sortedBy{time[it.id] ?: it.savedAt};2->filtered.sortedByDescending{it.id};3->filtered.sortedBy{it.id};else->filtered.sortedByDescending{time[it.id] ?: it.savedAt}}
    }
    LaunchedEffect(folderId){selected=emptySet();manage=false;more=false;preview=null;if(gridFolder!=folderId){grid.scrollToItem(0);gridFolder=folderId}}
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}}
    LaunchedEffect(visible){if(preview !in visible)preview=null}

    LazyVerticalGrid(columns=GridCells.Fixed(if(inside)settings.columns else 1),state=grid,
        modifier=Modifier.fillMaxSize().testTag("local-library"),contentPadding=PaddingValues(12.dp),
        horizontalArrangement=Arrangement.spacedBy(if(settings.columns>=8)1.dp else settings.cardSpacing.dp),
        verticalArrangement=Arrangement.spacedBy(if(inside && settings.columns>=8)1.dp else 8.dp)){
        item(span={GridItemSpan(maxLineSpan)}){
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(inside){
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("folder-header")){
                        Row(Modifier.padding(horizontal=4.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                            ActionIcon(R.drawable.ic_back,text(R.string.ui_back_to_app),{folderId=null},Modifier.testTag("folder-back"))
                            Column(Modifier.weight(1f)){
                                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2)
                                Text(text(R.string.ui_posts_count,candidates.size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box{
                                ActionIcon(R.drawable.ic_more,text(R.string.ui_menu),{more=true},Modifier.testTag("folder-more"))
                                DropdownMenu(more,{more=false}){
                                    DropdownMenuItem(text={Text(text(R.string.ui_batch_manage))},onClick={more=false;manage=!manage;selected=emptySet()})
                                    for((index,label)in listOf(R.string.ui_sort_saved_desc,R.string.ui_sort_saved_asc,R.string.ui_sort_id_desc,R.string.ui_sort_id_asc).withIndex())
                                        DropdownMenuItem(text={Text(text(label))},onClick={more=false;app.librarySort(index)})
                                    if(folder!=null){
                                        if(folder.id!=1L){
                                            DropdownMenuItem(text={Text(text(R.string.ui_rename))},onClick={more=false;name=folder.name;rename=true;folderDialog=true})
                                            DropdownMenuItem(text={Text(text(R.string.ui_delete_folder))},onClick={more=false;deleting=true})
                                        }
                                        DropdownMenuItem(text={Text(text(R.string.ui_set_cover))},enabled=selected.size==1,onClick={more=false;operation{app.localLibrary.cover(folder.id,selected.single())}})
                                        DropdownMenuItem(text={Text(text(R.string.ui_move_up))},onClick={more=false;operation{app.localLibrary.reorder(folder.id,-1)}})
                                        DropdownMenuItem(text={Text(text(R.string.ui_move_down))},onClick={more=false;operation{app.localLibrary.reorder(folder.id,1)}})
                                    }
                                    DropdownMenuItem(text={Text(text(R.string.ui_batch_download))},enabled=!busy && candidates.any{state.decoded[it.id]?.let{p->p.isVideo && app.offlineMedia.existing(p)==null}==true},onClick={more=false;downloadConfirm=true})
                                }
                            }
                        }
                    }
                    if(manage)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(text(R.string.ui_selected_count,selected.size),style=MaterialTheme.typography.labelMedium)
                        OutlinedButton(enabled=selected.isNotEmpty(),onClick={moving=true}){Text(text(R.string.ui_move_selected))}
                        OutlinedButton(enabled=selected.isNotEmpty(),onClick={removing=true}){Text(text(R.string.ui_remove_selected))}
                        TextButton(onClick={manage=false;selected=emptySet()}){Text(text(R.string.ui_close))}
                    }
                }else{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(text(R.string.ui_local_library),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        Box{
                            ActionIcon(R.drawable.ic_more,text(R.string.ui_menu),{more=true},Modifier.testTag("library-more"))
                            DropdownMenu(more,{more=false}){
                                DropdownMenuItem(text={Text(text(R.string.ui_offline_usage,usage/(1024*1024)))},enabled=false,onClick={})
                                DropdownMenuItem(text={Text(text(R.string.ui_clear_offline_media))},enabled=!busy,onClick={more=false;clearMedia=true})
                            }
                        }
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        LibraryButton(R.drawable.ic_add,text(R.string.ui_new_folder)){name="";rename=false;folderDialog=true}
                        LibraryButton(R.drawable.ic_download,text(R.string.ui_import_library)){importer.launch(arrayOf("application/json","text/plain"))}
                        LibraryButton(R.drawable.ic_upload,text(R.string.ui_export_library)){exporter.launch("sakugapp-favorites.json")}
                    }
                }
                if(state.error)Text(text(R.string.ui_library_failed),color=MaterialTheme.colorScheme.error)
                message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                if(busy){
                    if(currentSize>0)LinearProgressIndicator(progress={(currentBytes.toDouble()/currentSize).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(text(R.string.ui_download_batch_progress,done,total),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={job?.cancel()}){Text(text(R.string.ui_cancel_download))}
                }
            }
        }
        if(!inside){
            item{FolderCard(text(R.string.ui_all_favorites),state.inFolder(null).size,state.inFolder(null).firstOrNull()?.let{state.decoded[it.id]},settings.wifiImages && metered,Modifier.testTag("folder-entry-0")){folderId=0L}}
            items(state.folders,key={"folder-${it.id}"}){f->
                val entries=state.inFolder(f.id);val ids=entries.map{it.id}.toSet()
                val cover=state.decoded[f.coverPostId?.takeIf{it in ids} ?: entries.firstOrNull()?.id]
                FolderCard(folderName(f,context),entries.size,cover,settings.wifiImages && metered,Modifier.testTag("folder-entry-${f.id}")){folderId=f.id}
            }
        }else{
            if(candidates.isEmpty())item(span={GridItemSpan(maxLineSpan)}){Text(text(R.string.ui_library_empty),Modifier.padding(8.dp))}
            items(candidates,key={it.id}){entry->state.decoded[entry.id]?.let{raw->
                val post=state.durations[raw.fileUrl]?.let{raw.copy(durationMs=it)} ?: raw
                Column{
                    if(manage)Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=post.id in selected,onCheckedChange={selected=if(it)selected+post.id else selected-post.id},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_select_post,post.id)})
                        Text("#${post.id}",style=MaterialTheme.typography.labelSmall)
                    }
                    PostCard(post,settings,metered,preview==post.id,{if(manage){selected=if(post.id in selected)selected-post.id else selected+post.id}else open(post,candidates.mapNotNull{state.decoded[it.id]})},
                        search,
                        {if(!metered || settings.mobilePreview)preview=if(preview==post.id)null else post.id else message=context.getString(R.string.ui_previews_on_metered_networks_are_disabled_in_settings)},
                        {duration->app.duration(post.fileUrl.orEmpty(),duration)})
                    if(app.offlineMedia.existing(post)!=null)Text(text(R.string.ui_offline_ready),style=MaterialTheme.typography.labelSmall)
                }
            }}
        }
    }
    PacedAlertDialog(visible=folderDialog,onDismissRequest={folderDialog=false},title={Text(text(if(rename)R.string.ui_rename else R.string.ui_new_folder))},text={OutlinedTextField(name,{name=it.take(80)},label={Text(text(R.string.ui_folder_name))})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={operation{if(rename && folder!=null)app.localLibrary.rename(folder.id,name)else app.localLibrary.createFolder(name);folderDialog=false}}){Text(text(if(rename)R.string.ui_rename else R.string.ui_create))}},dismissButton={TextButton(onClick={folderDialog=false}){Text(text(R.string.ui_cancel))}})
    if(deleting && folder!=null)AlertDialog(onDismissRequest={deleting=false},title={Text(text(R.string.ui_delete_folder))},text={Text(text(R.string.ui_relationship_delete_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.deleteFolder(folder.id);folderId=null;deleting=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={deleting=false}){Text(text(R.string.ui_cancel))}})
    if(removing)AlertDialog(onDismissRequest={removing=false},title={Text(text(R.string.ui_remove_selected))},text={Text(text(R.string.ui_relationship_delete_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.batch(selected,actualId,null);selected=emptySet();removing=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={removing=false}){Text(text(R.string.ui_cancel))}})
    if(moving)AlertDialog(onDismissRequest={moving=false},title={Text(text(R.string.ui_move_selected))},text={Column{
        Text(text(R.string.ui_move_explanation))
        state.folders.forEach{target->TextButton(onClick={operation{app.localLibrary.batch(selected,actualId,target.id);selected=emptySet();moving=false}}){Text(folderName(target,context))}}
    }},confirmButton={TextButton(onClick={moving=false}){Text(text(R.string.ui_cancel))}})
    importBody?.let{body->AlertDialog(onDismissRequest={importBody=null},title={Text(text(R.string.ui_import_library))},text={Text(text(R.string.ui_import_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.import(body);importBody=null;message=context.getString(R.string.ui_imported)}}){Text(text(R.string.ui_merge))}},dismissButton={TextButton(onClick={importBody=null}){Text(text(R.string.ui_cancel))}})}
    if(clearMedia)AlertDialog(onDismissRequest={clearMedia=false},title={Text(text(R.string.ui_clear_offline_media))},text={Text(text(R.string.ui_clear_offline_warning))},confirmButton={TextButton(onClick={operation{app.offlineMedia.clear();usage=0;clearMedia=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={clearMedia=false}){Text(text(R.string.ui_cancel))}})
    if(downloadConfirm)AlertDialog(onDismissRequest={downloadConfirm=false},title={Text(text(R.string.ui_batch_download))},text={Text(text(R.string.ui_batch_download_hint))},confirmButton={TextButton(onClick={
        downloadConfirm=false;val pending=state.inFolder(actualId).mapNotNull{state.decoded[it.id]}.filter{it.isVideo && app.offlineMedia.existing(it)==null}.take(OfflineLibraryMedia.BATCH_SIZE)
        busy=true;done=0;total=pending.size
        job=scope.launch{try{for(post in pending){currentBytes=0;currentSize=-1;var updated=0L;app.offlineMedia.download(post){received,length->val now=System.nanoTime();if(now-updated>100000000L || received==length){updated=now;scope.launch{currentBytes=received;currentSize=length}}};done++};message=context.getString(R.string.ui_video_saved)}catch(e:CancellationException){message=context.getString(R.string.ui_download_cancelled);throw e}catch(e:Exception){message=libraryError(context,e)}finally{busy=false;usage=app.offlineMedia.bytes()}}
    }){Text(text(R.string.ui_download_video))}},dismissButton={TextButton(onClick={downloadConfirm=false}){Text(text(R.string.ui_cancel))}})
}

@Composable private fun LibraryButton(icon:Int,label:String,click:()->Unit){
    OutlinedButton(onClick=click,shape=MaterialTheme.shapes.small,modifier=Modifier.heightIn(min=40.dp)){
        Icon(painterResource(icon),contentDescription=null,modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp));Text(label)
    }
}
@Composable private fun FolderCard(name:String,count:Int,cover:Post?,blockImages:Boolean,modifier:Modifier=Modifier,click:()->Unit){
    Card(onClick=click,modifier=modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(0.dp)){
        Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant){
                if(cover?.previewUrl!=null && !blockImages)AsyncImage(cover.previewUrl,null,Modifier.width(80.dp).height(52.dp),contentScale=ContentScale.Crop)
                else Box(Modifier.width(80.dp).height(52.dp),contentAlignment=Alignment.Center){Icon(painterResource(R.drawable.ic_folder),contentDescription=null)}
            }
            Column(Modifier.weight(1f)){
                Text(name,style=MaterialTheme.typography.titleSmall,maxLines=2)
                Text(text(R.string.ui_posts_count,count),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

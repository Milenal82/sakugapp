// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LibraryScreen(app:AppViewModel,search:(String)->Unit={},tags:()->Unit={},open:(Post,List<Post>)->Unit){
    val state by app.library.collectAsStateWithLifecycle();val context=LocalContext.current;val scope=rememberCoroutineScope()
    var folderId by rememberSaveable{mutableStateOf<Long?>(null)}
    var subfolderId by rememberSaveable{mutableStateOf<Long?>(null)}
    var rootOrder by remember{mutableStateOf(false)};var childOrder by remember{mutableStateOf(false)}
    var childDialog by remember{mutableStateOf(false)};var childEditing by remember{mutableStateOf<FavoriteSubfolder?>(null)}
    var childName by remember{mutableStateOf("")};var childDeleting by remember{mutableStateOf<FavoriteSubfolder?>(null)}
    var classifying by remember{mutableStateOf<Post?>(null)}
    val preferences by app.settings.collectAsStateWithLifecycle();val sort=preferences.librarySort
    var selected by remember{mutableStateOf(setOf<Long>())}
    var folderDialog by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
    var deleting by remember{mutableStateOf(false)};var removing by remember{mutableStateOf(false)};var moving by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var downloadConfirm by remember{mutableStateOf(false)}
    var queueOpen by remember{mutableStateOf(false)}
    val transferSummary by remember(app.batchDownloads){app.batchDownloads.state.map{s->s.tasks.count{it.status==BatchStatus.DOWNLOADING} to s.tasks.count{it.status==BatchStatus.COMPLETE}}.distinctUntilChanged()}.collectAsStateWithLifecycle(initialValue=0 to 0)
    val busy=transferSummary.first>0
    val notifications=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){}
    val downloadAccess=rememberDirectoryAccess(app.downloadStorage,DownloadStorage.Kind.BATCH){message=context.getString(R.string.ui_directory_error)}
    var mediaRevision by remember{mutableIntStateOf(0)}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){mediaRevision++}
    val offlineIds by produceState(emptySet<Long>(),state.decoded,mediaRevision,transferSummary.second){value=withContext(Dispatchers.IO){state.decoded.values.filter{it.isVideo && app.offlineUri(it)!=null}.map{it.id}.toSet()}}
    var usage by remember{mutableLongStateOf(app.offlineMedia.bytes())};var clearMedia by remember{mutableStateOf(false)}
    val folder=state.folders.firstOrNull{it.id==folderId}
    val inside=folderId!=null
    val actualId=folderId?.takeIf{it>0}
    val child=state.subfolders.firstOrNull{it.id==subfolderId && it.folderId==actualId}
    val children=remember(state.subfolders,actualId){state.subfolders.filter{it.folderId==actualId}}
    val parentTitle=if(folder==null)text(R.string.ui_all_favorites)else folderName(folder,context)
    val title=child?.name ?: parentTitle
    val settings=preferences.copy(lightTheme=themeIsLight(preferences.themeMode,isSystemInDarkTheme()),columns=GridPolicy.effective(preferences.columns,LocalConfiguration.current.smallestScreenWidthDp))
    val metered by remember(context){meteredNetwork(context.applicationContext)}.collectAsStateWithLifecycle(initialValue=true)
    var more by remember{mutableStateOf(false)}
    var manage by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<Long?>(null)}
    val grid=rememberLazyGridState()
    var gridFolder by rememberSaveable{mutableStateOf<String?>(null)}
    fun back(){if(subfolderId!=null)subfolderId=null else folderId=null}
    BackHandler(enabled=inside){back()}
    LaunchedEffect(folderId,state.folders){if(folderId!=null && folderId!=0L && state.folders.isNotEmpty() && folder==null)folderId=null}
    LaunchedEffect(subfolderId,state.subfolders){if(subfolderId!=null && child==null)subfolderId=null}

    fun operation(action:suspend()->Unit){scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){message=libraryError(context,e)}}}
    val candidates=remember(state,folderId,subfolderId,sort){
        val time=state.links.filter{actualId==null || it.folderId==actualId}.groupBy{it.postId}.mapValues{e->e.value.minOf{it.addedAt}}
        val filtered=if(child==null)state.inFolder(actualId)else state.inSubfolder(child.id)
        when(sort){1->filtered.sortedBy{time[it.id] ?: it.savedAt};2->filtered.sortedByDescending{it.id};3->filtered.sortedBy{it.id};else->filtered.sortedByDescending{time[it.id] ?: it.savedAt}}
    }
    LaunchedEffect(folderId,subfolderId){selected=emptySet();manage=false;more=false;preview=null;val key="$folderId/$subfolderId";if(gridFolder!=key){grid.scrollToItem(0);gridFolder=key}}
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}}
    LaunchedEffect(visible){if(preview !in visible)preview=null}

    LazyVerticalGrid(columns=GridCells.Fixed(if(inside)settings.columns else 1),state=grid,
        modifier=Modifier.fillMaxSize().testTag("local-library"),contentPadding=PaddingValues(12.dp),
        horizontalArrangement=Arrangement.spacedBy(GridSpacingPolicy.spacing(settings.cardSpacing).dp),
        verticalArrangement=Arrangement.spacedBy(if(inside)GridSpacingPolicy.spacing(settings.cardSpacing).dp else 8.dp)){
        item(span={GridItemSpan(maxLineSpan)}){
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(inside){
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("folder-header")){
                        Row(Modifier.padding(horizontal=4.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                            ActionIcon(R.drawable.ic_back,text(R.string.ui_back_to_app),::back,Modifier.testTag("folder-back"))
                            Column(Modifier.weight(1f)){
                                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2)
                                if(child!=null)Text(parentTitle,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.ui_post_quantity,candidates.size,candidates.size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box{
                                ActionIcon(R.drawable.ic_more,text(R.string.ui_menu),{more=true},Modifier.testTag("folder-more"))
                                TileMenu(more,{more=false}){
                                    AppMenuItem(icon=R.drawable.ic_tune,toned=true,selected=manage,text={Text(text(R.string.ui_batch_manage))},onClick={more=false;manage=!manage;selected=emptySet()})
                                    for((index,label)in listOf(R.string.ui_sort_saved_desc,R.string.ui_sort_saved_asc,R.string.ui_sort_id_desc,R.string.ui_sort_id_asc).withIndex())
                                        AppMenuItem(icon=R.drawable.ic_drag,selected=sort==index,text={Text(text(label))},onClick={more=false;app.librarySort(index)})
                                    MenuDivider()
                                    if(child!=null){
                                        AppMenuItem(icon=R.drawable.ic_edit,toned=true,text={Text(text(R.string.ui_rename))},onClick={more=false;childEditing=child;childName=child.name;childDialog=true})
                                        AppMenuItem(icon=R.drawable.ic_close,toned=true,destructive=true,text={Text(text(R.string.ui_delete_subfolder))},onClick={more=false;childDeleting=child})
                                    }else if(folder!=null){
                                        if(folder.id!=1L){
                                            AppMenuItem(icon=R.drawable.ic_edit,toned=true,text={Text(text(R.string.ui_rename))},onClick={more=false;name=folder.name;rename=true;folderDialog=true})
                                            AppMenuItem(icon=R.drawable.ic_close,toned=true,destructive=true,text={Text(text(R.string.ui_delete_folder))},onClick={more=false;deleting=true})
                                        }
                                        AppMenuItem(icon=R.drawable.ic_palette,toned=true,text={Text(text(R.string.ui_set_cover))},enabled=selected.size==1,onClick={more=false;operation{app.localLibrary.cover(folder.id,selected.single())}})
                                        AppMenuItem(icon=R.drawable.ic_up,toned=true,text={Text(text(R.string.ui_move_up))},onClick={more=false;operation{app.localLibrary.reorder(folder.id,-1)}})
                                        AppMenuItem(icon=R.drawable.ic_down,toned=true,text={Text(text(R.string.ui_move_down))},onClick={more=false;operation{app.localLibrary.reorder(folder.id,1)}})
                                    }
                                    AppMenuItem(icon=R.drawable.ic_download,toned=true,text={Text(text(R.string.ui_batch_download))},enabled=candidates.any{state.decoded[it.id]?.let{p->p.isVideo && p.id !in offlineIds}==true},onClick={more=false;downloadConfirm=true})
                                }
                            }
                        }
                    }
                    if(actualId!=null && child==null)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        LibraryButton(R.drawable.ic_add,text(R.string.ui_new_subfolder),Modifier.testTag("new-subfolder")){childEditing=null;childName="";childDialog=true}
                        if(children.size>1)LibraryButton(R.drawable.ic_drag,text(R.string.ui_order_subfolders)){childOrder=true}
                    }
                    if(manage)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(text(R.string.ui_selected_count,selected.size),style=MaterialTheme.typography.labelMedium)
                        if(child==null)OutlinedButton(enabled=selected.isNotEmpty(),onClick={moving=true}){Text(text(R.string.ui_move_selected))}
                        OutlinedButton(enabled=selected.isNotEmpty(),onClick={removing=true}){Text(text(if(child==null)R.string.ui_remove_selected else R.string.ui_remove_classification))}
                        TextButton(onClick={manage=false;selected=emptySet()}){Text(text(R.string.ui_close))}
                    }
                }else{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(text(R.string.ui_local_library),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        Box{
                            ActionIcon(R.drawable.ic_more,text(R.string.ui_menu),{more=true},Modifier.testTag("library-more"))
                            TileMenu(more,{more=false}){
                                AppMenuItem(text={Text(text(R.string.ui_offline_usage,usage/(1024*1024)))},enabled=false,onClick={})
                                AppMenuItem(icon=R.drawable.ic_close,toned=true,destructive=true,text={Text(text(R.string.ui_clear_offline_media))},enabled=!busy,onClick={more=false;clearMedia=true})
                            }
                        }
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        LibraryButton(R.drawable.ic_add,text(R.string.ui_new_folder)){name="";rename=false;folderDialog=true}
                        LibraryButton(R.drawable.ic_drag,text(R.string.ui_order_folders),Modifier.testTag("order-folders")){rootOrder=true}
                    }
                    BackupActions(app)
                }
                if(state.error)Text(text(R.string.ui_library_failed),color=MaterialTheme.colorScheme.error)
                message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                LibraryButton(R.drawable.ic_download,text(R.string.ui_download_queue),Modifier.testTag("open-batch-queue")){queueOpen=true}

            }
        }
        if(!inside){
            item{OutlinedCard(onClick=tags,modifier=Modifier.fillMaxWidth().testTag("tag-favorites-entry")){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(text(R.string.ui_tag_favorites),Modifier.weight(1f));val tags by app.savedTags.collectAsStateWithLifecycle();Text(tags.size.toString())}
            }}
            item{FolderCard(text(R.string.ui_all_favorites),state.inFolder(null).size,state.inFolder(null).firstOrNull()?.let{state.decoded[it.id]},false,Modifier.testTag("folder-entry-0")){folderId=0L}}
            items(state.folders,key={"folder-${it.id}"}){f->
                val entries=state.inFolder(f.id);val ids=entries.map{it.id}.toSet()
                val cover=state.decoded[f.coverPostId?.takeIf{it in ids} ?: entries.firstOrNull()?.id]
                FolderCard(folderName(f,context),entries.size,cover,false,Modifier.testTag("folder-entry-${f.id}")){folderId=f.id}
            }
        }else{
            if(child==null)items(children,key={"subfolder-${it.id}"},span={GridItemSpan(maxLineSpan)}){sub->
                SubfolderCard(sub,state.inSubfolder(sub.id).size,{subfolderId=sub.id},
                    {childEditing=sub;childName=sub.name;childDialog=true},{childDeleting=sub})
            }
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
                        {preview=if(preview==post.id)null else post.id},
                        {duration->app.duration(post.fileUrl.orEmpty(),duration)},onClassify=if(actualId!=null)({classifying=post})else null)
                    if(post.id in offlineIds)Text(text(R.string.ui_offline_ready),style=MaterialTheme.typography.labelSmall)
                }
            }}
        }
    }
    if(queueOpen)BatchQueuePanel(app.batchDownloads){queueOpen=false}
    if(rootOrder)FolderOrdering(state.folders.map{it.id to folderName(it,context)},{ids->operation{app.localLibrary.orderFolders(ids)}},{rootOrder=false})
    if(childOrder && actualId!=null)FolderOrdering(children.map{it.id to it.name},{ids->operation{app.localLibrary.orderSubfolders(actualId,ids)}},{childOrder=false})
    if(childDialog && actualId!=null)AlertDialog(onDismissRequest={childDialog=false},title={Text(text(if(childEditing==null)R.string.ui_new_subfolder else R.string.ui_rename))},text={
        OutlinedTextField(childName,{childName=it.take(80)},label={Text(text(R.string.ui_folder_name))},modifier=Modifier.testTag("subfolder-name"))
    },confirmButton={TextButton(enabled=childName.isNotBlank(),onClick={operation{
        val editing=childEditing;if(editing==null)app.localLibrary.createSubfolder(actualId,childName)else app.localLibrary.renameSubfolder(editing.id,childName);childDialog=false
    }},modifier=Modifier.testTag("confirm-subfolder")){Text(text(if(childEditing==null)R.string.ui_create else R.string.ui_rename))}},dismissButton={TextButton(onClick={childDialog=false}){Text(text(R.string.ui_cancel))}})
    childDeleting?.let{value->AlertDialog(onDismissRequest={childDeleting=null},title={Text(text(R.string.ui_delete_subfolder))},text={Text(text(R.string.ui_delete_subfolder_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.deleteSubfolder(value.id);if(subfolderId==value.id)subfolderId=null;childDeleting=null}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={childDeleting=null}){Text(text(R.string.ui_cancel))}})}
    classifying?.let{post->if(actualId!=null)AlertDialog(onDismissRequest={classifying=null},title={Text(text(R.string.ui_add_to_subfolder))},text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())){
            if(children.isEmpty())Text(text(R.string.ui_no_subfolders))
            children.forEach{sub->FolderSelectionTile(sub.name,state.classified(post.id,sub.id),Modifier.testTag("classify-${sub.id}")){present->operation{app.localLibrary.classify(post.id,actualId,sub.id,present)}}}
        }
    },confirmButton={TextButton(onClick={classifying=null}){Text(text(R.string.ui_close))}})}
    PacedAlertDialog(visible=folderDialog,onDismissRequest={folderDialog=false},title={Text(text(if(rename)R.string.ui_rename else R.string.ui_new_folder))},text={OutlinedTextField(name,{name=it.take(80)},label={Text(text(R.string.ui_folder_name))})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={operation{if(rename && folder!=null)app.localLibrary.rename(folder.id,name)else app.localLibrary.createFolder(name);folderDialog=false}}){Text(text(if(rename)R.string.ui_rename else R.string.ui_create))}},dismissButton={TextButton(onClick={folderDialog=false}){Text(text(R.string.ui_cancel))}})
    if(deleting && folder!=null)AlertDialog(onDismissRequest={deleting=false},title={Text(text(R.string.ui_delete_folder))},text={Text(text(R.string.ui_relationship_delete_warning))},confirmButton={TextButton(onClick={operation{app.localLibrary.deleteFolder(folder.id);folderId=null;deleting=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={deleting=false}){Text(text(R.string.ui_cancel))}})
    if(removing)AlertDialog(onDismissRequest={removing=false},title={Text(text(if(child==null)R.string.ui_remove_selected else R.string.ui_remove_classification))},text={Text(text(if(child==null)R.string.ui_relationship_delete_warning else R.string.ui_delete_subfolder_warning))},confirmButton={TextButton(onClick={operation{if(child==null)app.localLibrary.batch(selected,actualId,null)else selected.forEach{app.localLibrary.classify(it,child.folderId,child.id,false)};selected=emptySet();removing=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={removing=false}){Text(text(R.string.ui_cancel))}})
    if(moving)AlertDialog(onDismissRequest={moving=false},title={Text(text(R.string.ui_move_selected))},text={Column{
        Text(text(R.string.ui_move_explanation))
        state.folders.forEach{target->TextButton(onClick={operation{app.localLibrary.batch(selected,actualId,target.id);selected=emptySet();moving=false}}){Text(folderName(target,context))}}
    }},confirmButton={TextButton(onClick={moving=false}){Text(text(R.string.ui_cancel))}})
    if(clearMedia)AlertDialog(onDismissRequest={clearMedia=false},title={Text(text(R.string.ui_clear_offline_media))},text={Text(text(R.string.ui_clear_offline_warning))},confirmButton={TextButton(onClick={operation{app.offlineMedia.clear();usage=0;mediaRevision++;clearMedia=false}}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={clearMedia=false}){Text(text(R.string.ui_cancel))}})
    if(downloadConfirm)AlertDialog(onDismissRequest={downloadConfirm=false},title={Text(text(R.string.ui_batch_download))},text={Text(text(R.string.ui_batch_download_hint))},confirmButton={TextButton(onClick={
        downloadConfirm=false;downloadAccess{val pending=candidates.mapNotNull{state.decoded[it.id]}.filter{it.isVideo && it.id !in offlineIds}.take(OfflineLibraryMedia.BATCH_SIZE)
        if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        app.batchDownloads.enqueue(pending);queueOpen=true}

    }){Text(text(R.string.ui_download_video))}},dismissButton={TextButton(onClick={downloadConfirm=false}){Text(text(R.string.ui_cancel))}})
}

@Composable private fun LibraryButton(icon:Int,label:String,modifier:Modifier=Modifier,click:()->Unit){
    OutlinedButton(onClick=click,shape=MaterialTheme.shapes.small,modifier=modifier.heightIn(min=40.dp)){
        Icon(painterResource(icon),contentDescription=null,modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp));Text(label)
    }
}

@Composable private fun SubfolderCard(folder:FavoriteSubfolder,count:Int,open:()->Unit,rename:()->Unit,delete:()->Unit){
    var more by remember(folder.id){mutableStateOf(false)}
    Card(onClick=open,modifier=Modifier.fillMaxWidth().testTag("subfolder-entry-${folder.id}"),shape=MaterialTheme.shapes.medium,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)){
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(painterResource(R.drawable.ic_folder),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(10.dp)){
                Text(folder.name,style=MaterialTheme.typography.titleSmall)
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.ui_post_quantity,count,count),style=MaterialTheme.typography.bodySmall)
            }
            Box{
                ActionIcon(R.drawable.ic_more,text(R.string.ui_menu),{more=true},Modifier.testTag("subfolder-menu-${folder.id}"))
                TileMenu(more,{more=false}){
                    AppMenuItem(icon=R.drawable.ic_edit,toned=true,text={Text(text(R.string.ui_rename))},onClick={more=false;rename()})
                    AppMenuItem(icon=R.drawable.ic_close,toned=true,destructive=true,text={Text(text(R.string.ui_delete_subfolder))},onClick={more=false;delete()})
                }
            }
        }
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
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.ui_post_quantity,count,count),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

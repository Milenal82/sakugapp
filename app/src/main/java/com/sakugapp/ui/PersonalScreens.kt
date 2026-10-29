// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.*

@Composable fun TagFavoritesScreen(app:AppViewModel,back:()->Unit,search:(String)->Unit){
    val tags by app.savedTags.collectAsStateWithLifecycle();val notes by app.tagNotes.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")};var sort by rememberSaveable{mutableIntStateOf(0)}
    var sorting by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<Tag?>(null)}
    val failure by app.personalError.collectAsStateWithLifecycle();val context=LocalContext.current
    val list=rememberLazyListState();val byName=remember(notes){notes.associate{it.name to it.note}}
    val shown=remember(tags,notes,query,sort){PersonalRules.searchable(tags,notes,query,sort)}
    val settings by app.settings.collectAsStateWithLifecycle();val light=themeIsLight(settings.themeMode,androidx.compose.foundation.isSystemInDarkTheme())
    Column(Modifier.fillMaxSize().testTag("tag-favorites-page")){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            ActionIcon(R.drawable.ic_back,text(R.string.ui_back_to_app),back)
            Text(text(R.string.ui_tag_favorites),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            Box{ActionIcon(R.drawable.ic_more,text(R.string.ui_sort_tags),{sorting=true},Modifier.testTag("sort-tags"))
                DropdownMenu(sorting,{sorting=false}){listOf(R.string.ui_tags_recent,R.string.ui_tags_name,R.string.ui_tags_oldest).forEachIndexed{i,label->DropdownMenuItem(text={Text(text(label))},onClick={sort=i;sorting=false})}}
            }
        }
        OutlinedTextField(query,{query=it},label={Text(text(R.string.ui_find_tags_notes))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp).testTag("saved-tags-search"))
        failure?.let{Text(libraryError(context,it),color=MaterialTheme.colorScheme.error)}
        if(tags.isEmpty())Text(text(R.string.ui_tag_favorites_empty),Modifier.padding(16.dp))
        else if(shown.isEmpty())Text(text(R.string.ui_no_results),Modifier.padding(16.dp))
        LazyColumn(state=list,contentPadding=PaddingValues(12.dp),modifier=Modifier.weight(1f)){
            items(shown,key={it.name}){entry->
                val tag=Tag(entry.name,PersonalRules.category(entry.category))
                Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f).clickable{search(tag.name)}.padding(8.dp).testTag("saved-tag-${tag.name}")){
                        Text(tag.name,color=tagColor(tag,light) ?: MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
                        byName[tag.name]?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    }
                    ActionIcon(R.drawable.ic_more,text(R.string.ui_tag_options),{selected=tag},Modifier.testTag("saved-tag-options-${tag.name}"))
                };HorizontalDivider()
            }
        }
    }
    selected?.let{TagActionsDialog(it,app){selected=null}}
}

@Composable fun BrowsingHistoryScreen(app:AppViewModel,back:()->Unit,open:(Post,List<Post>)->Unit){
    val rows by app.viewedPosts.collectAsStateWithLifecycle();val recording by app.recordHistory.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=LocalContext.current;val list=rememberLazyListState()
    var restoreIndex by rememberSaveable{mutableIntStateOf(-1)};var restoreOffset by rememberSaveable{mutableIntStateOf(0)}
    val restoreOnEntry=remember{restoreIndex>=0}
    LaunchedEffect(rows,restoreOnEntry){if(restoreOnEntry && restoreIndex>=0 && rows.isNotEmpty()){list.requestScrollToItem(restoreIndex,restoreOffset);restoreIndex=-1}}
    val failure by app.personalError.collectAsStateWithLifecycle()
    var clear by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val decoded=remember(rows){rows.mapNotNull{row->runCatching{row to LibraryCodec.decodePost(row.snapshot)}.getOrNull()}}
    val groups=remember(decoded){decoded.groupBy{Instant.ofEpochMilli(it.first.viewedAt).atZone(ZoneId.systemDefault()).toLocalDate()}}
    val settings by app.settings.collectAsStateWithLifecycle();val metered by remember{meteredNetwork(context)}.collectAsStateWithLifecycle(initialValue=true)
    val locale=LocalConfiguration.current.locales[0]
    fun operation(action:suspend()->Unit){scope.launch{try{action();error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=libraryError(context,e)}}}
    Column(Modifier.fillMaxSize().testTag("browsing-history-page")){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            ActionIcon(R.drawable.ic_back,text(R.string.ui_back_to_app),back)
            Text(text(R.string.ui_browsing_history),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            TextButton(enabled=rows.isNotEmpty(),onClick={clear=true},modifier=Modifier.testTag("clear-browsing-history")){Text(text(R.string.ui_clear))}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            Text(text(R.string.ui_record_browsing_history),Modifier.weight(1f));Switch(recording,{operation{app.personal.recording(it)}},modifier=Modifier.testTag("record-browsing-history").semantics{contentDescription=context.getString(R.string.ui_record_browsing_history)})
        }
        Text(text(R.string.ui_history_retention,PersonalRules.HISTORY_CAPACITY),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=12.dp))
        failure?.let{Text(libraryError(context,it),color=MaterialTheme.colorScheme.error)}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(rows.isEmpty())Text(text(R.string.ui_browsing_history_empty),Modifier.padding(16.dp))
        LazyColumn(state=list,contentPadding=PaddingValues(12.dp),modifier=Modifier.weight(1f).testTag("browsing-history-list")){
            groups.forEach{(date,entries)->
                item(key="day-$date"){Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(vertical=8.dp))}
                items(entries,key={it.first.id}){(row,post)->
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        Row(Modifier.weight(1f).clickable{restoreIndex=list.firstVisibleItemIndex;restoreOffset=list.firstVisibleItemScrollOffset;open(post,decoded.map{it.second})}.testTag("viewed-post-${post.id}"),verticalAlignment=Alignment.CenterVertically){
                            if(!settings.wifiImages || !metered)AsyncImage(post.previewUrl,null,Modifier.size(88.dp,52.dp),contentScale=ContentScale.Fit)
                            Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                                Text("#${post.id}",style=MaterialTheme.typography.labelLarge)
                                Text(post.tags.joinToString(" "){it.name},maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                                Text(Instant.ofEpochMilli(row.viewedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ActionIcon(R.drawable.ic_close,text(R.string.ui_remove_history_entry),{operation{app.personal.removeHistory(post.id)}},Modifier.testTag("remove-viewed-${post.id}"))
                    };HorizontalDivider()
                }
            }
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text(text(R.string.ui_clear_browsing_history))},text={Text(text(R.string.ui_clear_browsing_history_confirm))},confirmButton={TextButton(onClick={operation{app.personal.clearHistory();clear=false}},modifier=Modifier.testTag("confirm-clear-browsing-history")){Text(text(R.string.ui_clear))}},dismissButton={TextButton(onClick={clear=false}){Text(text(R.string.ui_cancel))}})
}

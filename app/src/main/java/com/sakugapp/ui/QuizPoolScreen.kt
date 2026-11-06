// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

/** Selection is auto-saved. Opening this page never fetches posts for saved works. */
@Composable internal fun QuizPoolScreen(app:AppViewModel){
    val config by app.quizSettings.collectAsStateWithLifecycle()
    var query by rememberSaveable(stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(""))}
    var candidates by remember{mutableStateOf(emptyList<TagSuggestion>())};var loading by remember{mutableStateOf(false)}
    var searchError by remember{mutableStateOf<String?>(null)};var saveError by remember{mutableStateOf<String?>(null)}
    var retry by remember{mutableIntStateOf(0)};var clear by rememberSaveable{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun save(action:suspend()->Unit){scope.launch{try{action();saveError=null}catch(e:CancellationException){throw e}catch(e:Exception){saveError=if(e.message=="quiz_pool_limit")"quiz_pool_limit"else"quiz_pool_save"}}}
    // Candidates never replace the editor text, so an IME composing span need not block lookup.
    LaunchedEffect(query.text,config.custom,retry){
        candidates=emptyList();searchError=null;loading=false
        val value=query.text.trim().replace(Regex("\\s+"),"_")
        if(config.custom && value.length>=2){
            loading=true
            try{delay(350);candidates=app.suggestions.suggestions(value).filter{it.category==TagCategory.COPYRIGHT && LibraryCodec.tagName(it.name)}.distinctBy{it.name}.take(8)}
            catch(e:CancellationException){throw e}catch(e:Exception){searchError=e.message ?: "Network connection failed. Check the connection and retry."}finally{loading=false}
        }
    }
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        LazyColumn(state=rememberLazyListState(),modifier=Modifier.widthIn(max=700.dp).fillMaxSize().imePadding().testTag("pool-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{Text(text(R.string.ui_pool_manage),style=MaterialTheme.typography.titleLarge)}
            item{
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf(false to R.string.ui_all_works,true to R.string.ui_custom_pool).forEach{(custom,label)->
                        FeatureSurface(Modifier.fillMaxWidth().testTag(if(custom)"pool-mode-custom"else"pool-mode-all"),onClick={save{app.preferences.quizMode(custom)}},selected=config.custom==custom){
                            Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=config.custom==custom,onClick=null);Text(text(label),Modifier.padding(8.dp))}
                        }
                    }
                }
            }
            item{saveError?.let{Text(extensionError(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("pool-save-error"))}}
            if(config.custom){
                item{Text(text(R.string.ui_pool_find),style=MaterialTheme.typography.titleMedium)}
                item{OutlinedTextField(query,{query=it},singleLine=true,placeholder={Text(text(R.string.ui_pool_search_hint))},modifier=Modifier.fillMaxWidth().testTag("pool-query"),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false))}
                item{
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(query.text.trim().length==1)Text(text(R.string.ui_pool_min_chars),style=MaterialTheme.typography.bodySmall)
                    searchError?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text(text(R.string.ui_retry))}}
                    if(query.text.trim().length>=2 && !loading && searchError==null && candidates.isEmpty())Text(text(R.string.ui_no_matching_verified_tags))
                }
                items(candidates,key={"candidate:"+it.name}){tag->
                    val selected=tag.name in config.works
                    FeatureSurface(Modifier.fillMaxWidth().testTag("pool-candidate-${tag.name}")){
                        Row(Modifier.padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text(tag.name,color=copyrightColor(MaterialTheme.colorScheme.background.luminance()>.5f));tag.count?.let{Text(text(R.string.ui_pool_post_count,it),style=MaterialTheme.typography.bodySmall)}}
                            ActionIcon(if(selected)R.drawable.ic_check else R.drawable.ic_add,text(if(selected)R.string.ui_pool_added else R.string.ui_pool_add),{if(!selected)save{app.preferences.quizWork(tag.name)}},Modifier.testTag("pool-add-${tag.name}"),enabled=!selected,selected=selected)
                        }
                    }
                }
                item{HorizontalDivider();Text(text(R.string.ui_pool_selected,config.works.size,QuizSettings.MAX_WORKS),style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("pool-count"))}
                item{if(config.works.isNotEmpty())TextButton(onClick={clear=true},modifier=Modifier.testTag("pool-clear")){Text(text(R.string.ui_clear_pool))}else Text(text(R.string.ui_quiz_choose_work),style=MaterialTheme.typography.bodySmall)}
                items(config.works,key={"selected:"+it}){tag->
                    Row(Modifier.fillMaxWidth().testTag("pool-selected-$tag"),verticalAlignment=Alignment.CenterVertically){Text(tag,Modifier.weight(1f));ActionIcon(R.drawable.ic_close,text(R.string.ui_pool_remove,tag),{save{app.preferences.quizRemoveWork(tag)}},Modifier.testTag("pool-remove-$tag"))}
                }
            }else item{Text(text(R.string.ui_pool_all_note),style=MaterialTheme.typography.bodySmall)}
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text(text(R.string.ui_clear_pool))},text={Text(text(R.string.ui_pool_clear_confirm))},confirmButton={TextButton(onClick={clear=false;save{app.preferences.quizClearWorks()}},modifier=Modifier.testTag("pool-confirm-clear")){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={clear=false}){Text(text(R.string.ui_cancel))}})
}

// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

@Composable internal fun LocalAvatar(profile:LocalProfile,size:Int){
    if(profile.avatar!=null)AsyncImage(profile.avatar,null,Modifier.size(size.dp).clip(CircleShape),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    else Surface(Modifier.size(size.dp),shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainerHigh){Icon(painterResource(R.drawable.ic_profile),null,Modifier.padding((size/5).dp))}
}
@Composable internal fun HomeTile(icon:Int,title:String,subtitle:String,modifier:Modifier=Modifier,click:()->Unit){
    Surface(onClick=click,modifier=modifier.heightIn(min=112.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Icon(painterResource(icon),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun SearchHomeScreen(app:AppViewModel,settings:AppSettings,search:(String)->Unit,route:(String)->Unit){
    var query by rememberSaveable(stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(""))};var focused by remember{mutableStateOf(false)}
    var combined by rememberSaveable{mutableStateOf(false)}
    val history by app.history.collectAsStateWithLifecycle();val keyboard=LocalSoftwareKeyboardController.current
    val suggestions=rememberSuggestions(query,focused,app.suggestions)
    fun submit(value:String){keyboard?.hide();search(value)}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag("search-home"),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused}.testTag("home-search-input"),placeholder={Text(text(R.string.ui_home_search_hint))},singleLine=true,
            leadingIcon={Icon(painterResource(R.drawable.ic_search),null)},trailingIcon={Row{if(query.text.isNotEmpty())ActionIcon(R.drawable.ic_close,text(R.string.ui_clear_search),{query=TextFieldValue("")});ActionIcon(R.drawable.ic_search,text(R.string.ui_search),{submit(query.text)},Modifier.testTag("home-submit"))}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query.text)}))
        SearchSyntax.warnings(query.text).forEach{Text(translateMessage(it),style=MaterialTheme.typography.labelSmall)}
        TagSuggestionsMenu(suggestions,settings.lightTheme){tag->if(query.composition==null && query.selection.collapsed){val edit=SearchEditing.complete(query.text,query.selection.start,tag);query=TextFieldValue(edit.text,TextRange(edit.cursor))}}
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            HomeTile(R.drawable.ic_tune,text(R.string.ui_home_combined),text(R.string.ui_home_combined_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-combined")){combined=true}
            HomeTile(R.drawable.ic_history,text(R.string.ui_home_search_history),text(R.string.ui_home_history_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-history")){route("search_history")}
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            HomeTile(R.drawable.ic_history,text(R.string.ui_home_browsing),text(R.string.ui_home_browsing_sub),Modifier.weight(1f).fillMaxHeight()){route("history")}
            HomeTile(R.drawable.ic_book,text(R.string.ui_home_shows),text(R.string.ui_home_shows_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-shows")){route("show_browser")}
        }
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){HomeTile(R.drawable.ic_profile,text(R.string.ui_home_artists),text(R.string.ui_home_artists_sub),Modifier.width((maxWidth-12.dp)/2).testTag("home-artists")){route("artist_stats")}}
    }}
    if(combined)SearchDialog(query.text,app.suggestions,settings.lightTheme,{combined=false;query=TextFieldValue(it);submit(it)},{combined=false},history)
}
@Composable internal fun SearchHistoryScreen(app:AppViewModel,search:(String)->Unit){
    val history by app.history.collectAsStateWithLifecycle();var clear by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text(text(R.string.ui_home_search_history),style=MaterialTheme.typography.titleLarge)
        if(history.isEmpty())Text(text(R.string.ui_no_saved_searches))
        history.forEach{query->Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={search(query)},modifier=Modifier.weight(1f)){Text(query)};ActionIcon(R.drawable.ic_close,text(R.string.ui_delete_search,query),{app.deleteHistory(query)})}}
        if(history.isNotEmpty())OutlinedButton(onClick={clear=true}){Text(text(R.string.ui_clear_search_history))}
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text(text(R.string.ui_clear_search_history))},confirmButton={TextButton(onClick={app.clearHistory();clear=false}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={clear=false}){Text(text(R.string.ui_cancel))}})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ProfileHomeScreen(app:AppViewModel,route:(String)->Unit,site:(String)->Unit){
    val profile by app.localProfile.collectAsStateWithLifecycle();val account by app.account.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    val displayedName=profile.nickname ?: text(R.string.ui_local_user)
    var edit by rememberSaveable{mutableStateOf(false)};var name by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
    val choose=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)scope.launch{busy=true;error=false;try{app.localProfileStore.avatar(uri)}catch(e:CancellationException){throw e}catch(_:Exception){error=true}finally{busy=false}}}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("profile-home"),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.fillMaxWidth().padding(10.dp)){
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                LocalAvatar(profile,68)
                Column(Modifier.weight(1f)){
                    Text(displayedName,style=MaterialTheme.typography.titleMedium)
                    FlowRow{TextButton(enabled=!busy,onClick={choose.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text(text(R.string.ui_change_avatar))};TextButton(onClick={name=displayedName;edit=true}){Text(text(R.string.ui_edit_nickname))}}
                }
            }
            Row(Modifier.align(androidx.compose.ui.Alignment.End)){
                ActionIcon(R.drawable.ic_rotate,text(R.string.ui_default_avatar),{scope.launch{try{app.localProfileStore.resetAvatar();error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},Modifier.testTag("reset-avatar"),enabled=!busy,iconSize=20.dp)
                ActionIcon(R.drawable.ic_edit,text(R.string.ui_default_nickname),{scope.launch{try{app.localProfileStore.nickname(null);error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},Modifier.testTag("reset-nickname"),iconSize=20.dp)
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error)Text(text(R.string.ui_profile_error),color=MaterialTheme.colorScheme.error)
        }}
        HomeTile(R.drawable.ic_folder,text(R.string.ui_my_folders),text(R.string.ui_folders_sub),Modifier.fillMaxWidth()){route("library")}
        Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            HomeTile(R.drawable.ic_profile,text(if(account==null)R.string.ui_account_login else R.string.ui_website_account),"Sakugabooru",Modifier.weight(1f).fillMaxHeight()){route("account")}
            HomeTile(R.drawable.ic_book,text(R.string.ui_blog),"Sakugabooru Blog",Modifier.weight(1f).fillMaxHeight()){site("https://blog.sakugabooru.com/")}
        }
        Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            HomeTile(R.drawable.ic_book,text(R.string.ui_forum),"Sakugabooru Forum",Modifier.weight(1f).fillMaxHeight()){site("https://www.sakugabooru.com/forum")}
            HomeTile(R.drawable.ic_tune,text(R.string.ui_settings),text(R.string.ui_settings_sub),Modifier.weight(1f).fillMaxHeight()){route("settings")}
        }
    }}
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text(text(R.string.ui_edit_nickname))},text={Column{OutlinedTextField(name,{name=it},singleLine=true,modifier=Modifier.testTag("nickname-input"));if(error)Text(text(R.string.ui_profile_error))}},confirmButton={TextButton(onClick={scope.launch{try{app.localProfileStore.nickname(name);edit=false;error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},modifier=Modifier.testTag("save-nickname")){Text(text(R.string.ui_save_edit))}},dismissButton={TextButton(onClick={edit=false;error=false}){Text(text(R.string.ui_cancel))}})
}

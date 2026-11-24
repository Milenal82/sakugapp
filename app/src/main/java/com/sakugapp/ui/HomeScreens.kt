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
import androidx.compose.ui.semantics.Role
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
@Composable internal fun SearchHomeScreen(app:AppViewModel,settings:AppSettings,search:(String)->Unit,route:(String)->Unit){
    var query by rememberSaveable(key="home-query",stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(""))};var focused by remember{mutableStateOf(false)}
    var combined by rememberSaveable(key="home-combined"){mutableStateOf(false)}
    val history by app.history.collectAsStateWithLifecycle();val keyboard=LocalSoftwareKeyboardController.current
    val scroll=rememberSaveable(key="home-scroll",saver=ScrollState.Saver){ScrollState(0)}
    val suggestions=rememberSuggestions(query,focused,app.suggestions)
    fun submit(value:String){keyboard?.hide();search(value)}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=600.dp).fillMaxSize().searchElasticScroll(scroll).imePadding().padding(16.dp).testTag("search-home"),verticalArrangement=Arrangement.spacedBy(FeatureStyle.gap)){
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused}.testTag("home-search-input"),placeholder={Text(text(R.string.ui_home_search_hint),maxLines=1,overflow=TextOverflow.Ellipsis)},singleLine=true,
            leadingIcon={Icon(painterResource(R.drawable.ic_search),null)},trailingIcon={Row{if(query.text.isNotEmpty())ActionIcon(R.drawable.ic_close,text(R.string.ui_clear_search),{query=TextFieldValue("")});ActionIcon(R.drawable.ic_search,text(R.string.ui_search),{submit(query.text)},Modifier.testTag("home-submit"))}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query.text)}))
        SearchSyntax.warnings(query.text).forEach{Text(translateMessage(it),style=MaterialTheme.typography.labelSmall)}
        TagSuggestionsMenu(suggestions,settings.lightTheme){tag->if(query.composition==null && query.selection.collapsed){val edit=SearchEditing.complete(query.text,query.selection.start,tag);query=TextFieldValue(edit.text,TextRange(edit.cursor))}}
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(FeatureStyle.gap)){
            HomeTile(R.drawable.ic_tune,text(R.string.ui_home_combined),text(R.string.ui_home_combined_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-combined")){combined=true}
            HomeTile(R.drawable.ic_history,text(R.string.ui_home_search_history),text(R.string.ui_home_history_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-history")){route("search_history")}
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(FeatureStyle.gap)){
            HomeTile(R.drawable.ic_history,text(R.string.ui_home_browsing),text(R.string.ui_home_browsing_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-browsing")){route("history")}
            HomeTile(R.drawable.ic_movie,text(R.string.ui_home_shows),text(R.string.ui_home_shows_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-shows")){route("show_browser")}
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(FeatureStyle.gap)){
            HomeTile(R.drawable.ic_groups,text(R.string.ui_home_artists),text(R.string.ui_home_artists_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-artists")){route("artist_stats")}
            HomeTile(R.drawable.ic_explore,text(R.string.ui_explore),text(R.string.ui_explore_sub),Modifier.weight(1f).fillMaxHeight().testTag("home-explore")){route("explore")}
        }
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){HomeTile(R.drawable.ic_star,text(R.string.ui_quiz),text(R.string.ui_quiz_sub),Modifier.width((maxWidth-FeatureStyle.gap)/2).testTag("home-quiz")){route("quiz")}}
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
    val scroll=rememberSaveable(key="profile-scroll",saver=ScrollState.Saver){ScrollState(0)}
    var edit by rememberSaveable(key="profile-edit"){mutableStateOf(false)};var name by rememberSaveable(key="profile-name"){mutableStateOf("")};var error by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
    val choose=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)scope.launch{busy=true;error=false;try{app.localProfileStore.avatar(uri)}catch(e:CancellationException){throw e}catch(_:Exception){error=true}finally{busy=false}}}
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        val wide=maxWidth>=600.dp
        val actions:@Composable ()->Unit={BoxWithConstraints(Modifier.fillMaxWidth()){
                    FlowRow(maxItemsInEachRow=if(maxWidth<192.dp)2 else 4,horizontalArrangement=if(wide)Arrangement.Center else Arrangement.End){
                        ActionIcon(R.drawable.ic_profile,text(R.string.ui_change_avatar),{choose.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},Modifier.testTag("change-avatar"),enabled=!busy,iconSize=22.dp)
                        ActionIcon(R.drawable.ic_edit,text(R.string.ui_edit_nickname),{name=displayedName;edit=true},Modifier.testTag("edit-nickname"),iconSize=22.dp)
                        ActionIcon(R.drawable.ic_rotate,text(R.string.ui_default_avatar),{scope.launch{try{app.localProfileStore.resetAvatar();error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},Modifier.testTag("reset-avatar"),enabled=!busy,iconSize=20.dp)
                        ActionIcon(R.drawable.ic_close,text(R.string.ui_default_nickname),{scope.launch{try{app.localProfileStore.nickname(null);error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},Modifier.testTag("reset-nickname"),iconSize=20.dp)
        }}
        val identity:@Composable ()->Unit={
            FeatureSurface(Modifier.fillMaxWidth().testTag("profile-card")){Column(Modifier.padding(FeatureStyle.inset),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(wide)Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                    LocalAvatar(profile,68);Text(displayedName,style=MaterialTheme.typography.titleMedium);actions()
                }else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    LocalAvatar(profile,68);Column(Modifier.weight(1f)){
                        Text(displayedName,style=MaterialTheme.typography.titleMedium);actions()
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error)Text(text(R.string.ui_profile_error),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }}
        }
        Column(Modifier.widthIn(max=960.dp).fillMaxWidth().verticalScroll(scroll).padding(16.dp).testTag("profile-home"),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(wide)Row(Modifier.fillMaxWidth().testTag("profile-two-column"),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top){
                Box(Modifier.weight(.35f)){identity()}
                Box(Modifier.weight(.65f)){ProfileLinks(account!=null,route,site)}
            }else Column(Modifier.fillMaxWidth().testTag("profile-single-column"),verticalArrangement=Arrangement.spacedBy(12.dp)){
                identity();ProfileLinks(account!=null,route,site)
            }
        }
    }
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text(text(R.string.ui_edit_nickname))},text={Column{OutlinedTextField(name,{name=it},singleLine=true,modifier=Modifier.testTag("nickname-input"));if(error)Text(text(R.string.ui_profile_error))}},confirmButton={TextButton(onClick={scope.launch{try{app.localProfileStore.nickname(name);edit=false;error=false}catch(e:CancellationException){throw e}catch(_:Exception){error=true}}},modifier=Modifier.testTag("save-nickname")){Text(text(R.string.ui_save_edit))}},dismissButton={TextButton(onClick={edit=false;error=false}){Text(text(R.string.ui_cancel))}})
}

/** Six flat rows; destinations, account checks and local-profile storage are unchanged. */
@Composable private fun ProfileLinks(signedIn:Boolean,route:(String)->Unit,site:(String)->Unit){
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("profile-functions")){
        Column{
            ProfileLinkRow(R.drawable.ic_folder,text(R.string.ui_my_folders),"profile-folders"){route("library")}
            ProfileRowDivider()
            ProfileLinkRow(R.drawable.ic_star_outline,text(R.string.ui_website_favorites),"profile-favorites"){route("website_favorites")}
            ProfileRowDivider()
            ProfileLinkRow(R.drawable.ic_profile,text(if(signedIn)R.string.ui_website_account else R.string.ui_account_login),"profile-account",subtitle="Sakugabooru"){route("account")}
            ProfileRowDivider()
            ProfileLinkRow(R.drawable.ic_book,text(R.string.ui_blog),"profile-blog"){site("https://blog.sakugabooru.com/")}
            ProfileRowDivider()
            ProfileLinkRow(R.drawable.ic_book,text(R.string.ui_forum),"profile-forum"){site("https://www.sakugabooru.com/forum")}
            ProfileRowDivider()
            ProfileLinkRow(R.drawable.ic_settings,text(R.string.ui_settings),"profile-settings"){route("settings")}
        }
    }
}
@Composable private fun ProfileRowDivider(){HorizontalDivider(Modifier.padding(start=46.dp,end=12.dp),color=MaterialTheme.colorScheme.outlineVariant)}
@Composable private fun ProfileLinkRow(icon:Int,title:String,tag:String,subtitle:String?=null,click:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable(role=Role.Button,onClick=click).padding(horizontal=textInset(12.dp),vertical=textInset(8.dp)).testTag(tag),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(painterResource(icon),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)){
            Text(title,style=MaterialTheme.typography.bodyMedium)
            subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Icon(painterResource(R.drawable.ic_chevron_right),null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

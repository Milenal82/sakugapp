// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.painterResource
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.data.*
import kotlinx.coroutines.*

@Composable
fun AccountScreen(model:AppViewModel,openSite:(String)->Unit) {
    val context=LocalContext.current
    val account by model.account.collectAsStateWithLifecycle()
    val busy by model.authBusy.collectAsStateWithLifecycle()
    val error by model.authError.collectAsStateWithLifecycle()
    var name by rememberSaveable{mutableStateOf("")}
    // Deliberately not rememberSaveable/DataStore. Cleared immediately after submitting.
    var password by remember{mutableStateOf("")}
    val activity=LocalActivity.current
    DisposableEffect(account){if(account==null)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE);onDispose{activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(text(R.string.ui_my_account),style=MaterialTheme.typography.headlineMedium)
        account?.let{user->
            Text(user.name,style=MaterialTheme.typography.titleLarge)
            Button(onClick={openSite("https://www.sakugabooru.com/user/home")}){Text(text(R.string.ui_official_account_management))}
            OutlinedButton(enabled=!busy,onClick=model::logout){Text(text(R.string.ui_log_out_on_this_device))}
        } ?: run {
            OutlinedTextField(name,{name=it},label={Text(text(R.string.ui_username))},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(password,{password=it},label={Text(text(R.string.ui_password))},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            Button(enabled=!busy && name.isNotBlank() && password.isNotEmpty(),onClick={model.login(name,password);password=""}){Text(text(R.string.ui_log_in))}
            Text(text(R.string.ui_uses_the_official_https_login_endpoint_your_password_is_not_store),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={openSite("https://www.sakugabooru.com/user/login")}){Text(text(R.string.ui_registration_reset_on_website))}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error)}
    }
}

@Composable
fun VoteControl(post:Post,model:AppViewModel,onScore:(Int?)->Unit,onLogin:()->Unit){
    val account by model.account.collectAsStateWithLifecycle()
    val voteLabel=text(R.string.ui_vote)
    val vote=remember(post.id){model.postVote(post.id)}
    LaunchedEffect(post.id,account?.id){vote.identify(account)}
    var expanded by remember(post.id,account?.id){mutableStateOf(false)}
    Column{
        Box{
            ActionIcon(if((vote.value ?: 0)>0)R.drawable.ic_star else R.drawable.ic_star_outline,text(R.string.ui_vote),{if(account==null)onLogin()else expanded=true},Modifier.testTag("vote-icon"),touchSize=40.dp,enabled=!vote.busy,selected=(vote.value ?: 0)>0)
            TileMenu(expanded,{expanded=false}){
                for(value in 0..3)AppMenuItem(icon=null,selected=vote.value==value,text={
                    if(value==0)Text("0")else Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){repeat(value){Icon(painterResource(R.drawable.ic_star),contentDescription=null,modifier=Modifier.size(18.dp))}}
                },modifier=Modifier.testTag("vote-$value").semantics{contentDescription="$voteLabel $value"},onClick={expanded=false;vote.choose(value,onScore)})
            }
        }
        if(vote.busy)LinearProgressIndicator(Modifier.width(48.dp))
        vote.error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(onClick=vote::refresh){Text(text(R.string.ui_reload_vote))}}
        if(vote.confirmed)Text(text(R.string.ui_vote_confirmed_by_the_server),style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentsPanel(post:Post,model:AppViewModel,onLogin:()->Unit,seek:(Long)->Unit){
    val account by model.account.collectAsStateWithLifecycle()
    val thread=remember(post.id){model.commentThread(post.id)}
    var expanded by remember(post.id){mutableStateOf(false)}
    var deleting by remember(post.id){mutableStateOf<Comment?>(null)}
    Column(Modifier.fillMaxWidth()){
        Surface(onClick={expanded=!expanded;if(expanded)thread.expand()},color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().testTag("comments-toggle")){
            Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Text(text(R.string.ui_comments_section),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                Icon(painterResource(if(expanded)R.drawable.ic_up else R.drawable.ic_down),contentDescription=null,modifier=Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(expanded,enter=expandVertically(tween(Motion.controls)),exit=shrinkVertically(tween(Motion.controls))){Column(Modifier.fillMaxWidth().testTag("comments-content"),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(account==null)TextButton(onClick=onLogin){Text(text(R.string.ui_log_in_to_vote_or_comment))}
            else{
                OutlinedTextField(thread.draft,thread::draft,label={Text(text(if(thread.editing==null)R.string.ui_comment else R.string.ui_edit_your_comment))},modifier=Modifier.fillMaxWidth().testTag("comment-draft"),minLines=2,enabled=!thread.sending)
                Row{
                    Button(enabled=thread.draft.isNotBlank() && !thread.sending && !thread.loading,onClick=thread::submit){Text(text(if(thread.editing==null)R.string.ui_post_comment else R.string.ui_save_edit))}
                    if(thread.editing!=null)TextButton(onClick=thread::cancelEdit){Text(text(R.string.ui_cancel_edit))}
                }
            }
            if(thread.loading || thread.sending)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(thread.loaded && thread.comments.isEmpty() && !thread.loading && thread.error==null)Text(text(R.string.ui_no_comments_on_this_post))
            thread.comments.forEach{comment->Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("${comment.author ?: text(R.string.ui_unknown)} · ${comment.createdAt.orEmpty()}",style=MaterialTheme.typography.labelMedium)
                Text(comment.body)
                FlowRow{CommunityParser.timecodes(comment.body).forEach{(label,ms)->TextButton(onClick={seek(ms)}){Text(label)}}}
                if(account!=null && comment.authorId==account?.id)Row{
                    TextButton(enabled=!thread.sending,onClick={thread.edit(comment)}){Text(text(R.string.ui_edit))}
                    TextButton(enabled=!thread.sending && !thread.loading,onClick={deleting=comment}){Text(text(R.string.ui_delete))}
                }
            }}}
            thread.error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(enabled=!thread.loading,onClick=thread::retry){Text(text(R.string.ui_retry_comments))}}
            if(thread.loaded && !thread.loading && !thread.end && thread.error==null)TextButton(onClick=thread::more){Text(text(R.string.ui_more_comments))}
            thread.notice?.let{Text(text(it),style=MaterialTheme.typography.bodySmall)}
        }}
    }
    deleting?.let{target->AlertDialog(onDismissRequest={deleting=null},title={Text(text(R.string.ui_delete_this_comment))},text={Text(target.body.take(300))},confirmButton={TextButton(onClick={deleting=null;thread.delete(target)}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={deleting=null}){Text(text(R.string.ui_cancel))}})}
}

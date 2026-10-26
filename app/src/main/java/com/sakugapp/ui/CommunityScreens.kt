// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.activity.compose.LocalActivity
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
            Text(text(R.string.ui_saved_website_session_protected_actions_are_confirmed_by_the_serv))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityPanel(post:Post,model:AppViewModel,onScore:(Int?)->Unit,onLogin:()->Unit,seek:(Long)->Unit) {
    val context=LocalContext.current
    val account by model.account.collectAsStateWithLifecycle()
    var vote by remember(post.id,account?.id){mutableStateOf<Int?>(null)}
    var voteBusy by remember{mutableStateOf(false)}
    var voteError by remember(post.id){mutableStateOf<String?>(null)}
    var comments by remember(post.id){mutableStateOf<List<Comment>>(emptyList())}
    var page by remember(post.id){mutableIntStateOf(1)}
    var end by remember(post.id){mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var commentError by remember(post.id){mutableStateOf<String?>(null)}
    var draft by rememberSaveable(post.id){mutableStateOf("")}
    var editing by remember{mutableStateOf<Long?>(null)}
    var deleting by remember{mutableStateOf<Comment?>(null)}
    var sending by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    var retryReset by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    suspend fun load(reset:Boolean=false){
        if(loading)return
        loading=true;commentError=null
        try{
            retryReset=reset
            val requested=if(reset)1 else page
            val result=model.community.comments(post.id,requested)
            comments=((if(reset)emptyList()else comments)+result).distinctBy{it.id}
            page=requested+1;end=result.size<25
        }catch(e:CancellationException){throw e}catch(e:Exception){commentError=e.message}finally{loading=false}
    }
    suspend fun refreshVote(){voteError=null;try{vote=model.community.currentVote(post.id)}catch(e:CancellationException){throw e}catch(e:Exception){voteError=e.message}}
    LaunchedEffect(post.id){load(true)}
    LaunchedEffect(post.id,account?.id){if(account!=null)refreshVote()}
    HorizontalDivider();Text(text(R.string.ui_vote),style=MaterialTheme.typography.titleLarge)
    if(account==null)TextButton(onClick=onLogin){Text(text(R.string.ui_log_in_to_vote_or_comment))}
    else {
        Text(text(R.string.ui_0_clears_your_vote_3_is_the_site_s_favorite_level),style=MaterialTheme.typography.bodySmall)
        Row{for(score in 0..3)TextButton(enabled=!voteBusy,onClick={scope.launch{
            voteBusy=true;voteError=null
            try{val result=model.community.vote(post.id,score);onScore(result.score);refreshVote();notice=context.getString(R.string.ui_vote_confirmed_by_the_server)}
            catch(e:CancellationException){throw e}catch(e:Exception){voteError=e.message}finally{voteBusy=false}
        }}){Text(if(vote==score)"[$score]"else"$score")}}
        voteError?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch{refreshVote()}}){Text(text(R.string.ui_reload_vote))}}
    }
    HorizontalDivider();Text(text(R.string.ui_comments),style=MaterialTheme.typography.titleLarge)
    if(account!=null){
        OutlinedTextField(draft,{if(it.length<=20000)draft=it},label={Text(if(editing==null)text(R.string.ui_comment)else text(R.string.ui_edit_your_comment))},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=!sending)
        Row{
            Button(enabled=draft.isNotBlank() && !sending && !loading,onClick={scope.launch{
                sending=true;commentError=null
                try{model.community.comment(post.id,draft,editing);draft="";editing=null;notice=context.getString(R.string.ui_comment_accepted_by_the_server);load(true)}
                catch(e:CancellationException){throw e}catch(e:Exception){commentError=e.message}finally{sending=false}
            }}){Text(if(editing==null)text(R.string.ui_post_comment)else text(R.string.ui_save_edit))}
            if(editing!=null)TextButton(onClick={editing=null;draft=""}){Text(text(R.string.ui_cancel_edit))}
        }
    }
    if(loading || sending || voteBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(comments.isEmpty() && !loading && commentError==null)Text(text(R.string.ui_no_comments_on_this_post))
    comments.forEach{comment->
        Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("${comment.author ?: text(R.string.ui_unknown)} · ${comment.createdAt.orEmpty()}",style=MaterialTheme.typography.labelMedium)
                // Raw DText is displayed as text. Never evaluate scripts or render untrusted HTML.
                Text(comment.body)
                FlowRow{CommunityParser.timecodes(comment.body).forEach{(label,ms)->TextButton(onClick={seek(ms)}){Text(label)}}}
                if(account!=null && comment.authorId==account?.id)Row{
                    TextButton(enabled=!sending,onClick={editing=comment.id;draft=comment.body}){Text(text(R.string.ui_edit))}
                    TextButton(enabled=!sending && !loading,onClick={deleting=comment}){Text(text(R.string.ui_delete))}
                }
            }
        }
    }
    commentError?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(enabled=!loading,onClick={scope.launch{load(retryReset)}}){Text(text(R.string.ui_retry_comments))}}
    if(!loading && !end && commentError==null)TextButton(onClick={scope.launch{load()}}){Text(text(R.string.ui_more_comments))}
    notice?.let{Text(translateMessage(it),style=MaterialTheme.typography.bodySmall)}
    deleting?.let{target->AlertDialog(onDismissRequest={deleting=null},title={Text(text(R.string.ui_delete_this_comment))},text={Text(target.body.take(300))},confirmButton={TextButton(onClick={deleting=null;scope.launch{
        sending=true;try{model.community.deleteComment(post.id,target.id);notice=context.getString(R.string.ui_deletion_confirmed_by_the_server);load(true)}catch(e:CancellationException){throw e}catch(e:Exception){commentError=e.message}finally{sending=false}
    }}){Text(text(R.string.ui_delete))}},dismissButton={TextButton(onClick={deleting=null}){Text(text(R.string.ui_cancel))}})}
}

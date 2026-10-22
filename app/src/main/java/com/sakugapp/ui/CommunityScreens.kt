// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

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
    val account by model.account.collectAsStateWithLifecycle()
    val busy by model.authBusy.collectAsStateWithLifecycle()
    val error by model.authError.collectAsStateWithLifecycle()
    var name by rememberSaveable{mutableStateOf("")}
    // Deliberately not rememberSaveable/DataStore. Cleared immediately after submitting.
    var password by remember{mutableStateOf("")}
    val activity=LocalActivity.current
    DisposableEffect(account){if(account==null)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE);onDispose{activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(text("My Account","アカウント"),style=MaterialTheme.typography.headlineMedium)
        account?.let{user->
            Text(user.name,style=MaterialTheme.typography.titleLarge)
            Text(text("Saved website session. Protected actions are confirmed by the server; expired sessions may require login again.","保存されたサイトのセッションです。操作はサーバーで確認されます。期限切れの場合は再ログインが必要です。"))
            Button(onClick={openSite("https://www.sakugabooru.com/user/home")}){Text(text("Official account management","公式アカウント管理"))}
            OutlinedButton(enabled=!busy,onClick=model::logout){Text(text("Log out on this device","この端末からログアウト"))}
        } ?: run {
            OutlinedTextField(name,{name=it},label={Text(text("Username","ユーザー名"))},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(password,{password=it},label={Text(text("Password","パスワード"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            Button(enabled=!busy && name.isNotBlank() && password.isNotEmpty(),onClick={model.login(name,password);password=""}){Text(text("Log in","ログイン"))}
            Text(text("Uses the official HTTPS login endpoint. Your password is not stored. Session cookies are encrypted with Android Keystore. Registration and password reset are handled on the official site.","公式HTTPSログインを使用し、パスワードは保存しません。セッションCookieはAndroid Keystoreで暗号化します。新規登録とパスワード再設定は公式サイトで行います。"),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={openSite("https://www.sakugabooru.com/user/login")}){Text(text("Registration / reset on website","公式サイトで登録・再設定"))}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityPanel(post:Post,model:AppViewModel,onScore:(Int?)->Unit,onLogin:()->Unit,seek:(Long)->Unit) {
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
    HorizontalDivider();Text(text("Vote","評価"),style=MaterialTheme.typography.titleLarge)
    if(account==null)TextButton(onClick=onLogin){Text(text("Log in to vote or comment","評価・コメントにはログインが必要です"))}
    else {
        Text(text("0 clears your vote · 3 is the site's favorite level","0で評価解除・3はサイトのお気に入りレベル"),style=MaterialTheme.typography.bodySmall)
        Row{for(score in 0..3)TextButton(enabled=!voteBusy,onClick={scope.launch{
            voteBusy=true;voteError=null
            try{val result=model.community.vote(post.id,score);onScore(result.score);refreshVote();notice="Vote confirmed by the server."}
            catch(e:CancellationException){throw e}catch(e:Exception){voteError=e.message}finally{voteBusy=false}
        }}){Text(if(vote==score)"[$score]"else"$score")}}
        voteError?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch{refreshVote()}}){Text(text("Reload vote","評価を再読込"))}}
    }
    HorizontalDivider();Text(text("Comments","コメント"),style=MaterialTheme.typography.titleLarge)
    if(account!=null){
        OutlinedTextField(draft,{if(it.length<=20000)draft=it},label={Text(if(editing==null)text("Comment","コメント本文")else text("Edit your comment","コメント編集"))},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=!sending)
        Row{
            Button(enabled=draft.isNotBlank() && !sending && !loading,onClick={scope.launch{
                sending=true;commentError=null
                try{model.community.comment(post.id,draft,editing);draft="";editing=null;notice="Comment accepted by the server.";load(true)}
                catch(e:CancellationException){throw e}catch(e:Exception){commentError=e.message}finally{sending=false}
            }}){Text(if(editing==null)text("Post comment","投稿")else text("Save edit","編集を保存"))}
            if(editing!=null)TextButton(onClick={editing=null;draft=""}){Text(text("Cancel edit","編集を中止"))}
        }
    }
    if(loading || sending || voteBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(comments.isEmpty() && !loading && commentError==null)Text(text("No comments on this post.","コメントはありません。"))
    comments.forEach{comment->
        Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("${comment.author ?: "Unknown"} · ${comment.createdAt.orEmpty()}",style=MaterialTheme.typography.labelMedium)
                // Raw DText is displayed as text. Never evaluate scripts or render untrusted HTML.
                Text(comment.body)
                FlowRow{CommunityParser.timecodes(comment.body).forEach{(label,ms)->TextButton(onClick={seek(ms)}){Text(label)}}}
                if(account!=null && comment.authorId==account?.id)Row{
                    TextButton(enabled=!sending,onClick={editing=comment.id;draft=comment.body}){Text(text("Edit","編集"))}
                    TextButton(enabled=!sending && !loading,onClick={deleting=comment}){Text(text("Delete","削除"))}
                }
            }
        }
    }
    commentError?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(enabled=!loading,onClick={scope.launch{load(retryReset)}}){Text(text("Retry comments","コメントを再試行"))}}
    if(!loading && !end && commentError==null)TextButton(onClick={scope.launch{load()}}){Text(text("More comments","コメントをさらに表示"))}
    notice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    deleting?.let{target->AlertDialog(onDismissRequest={deleting=null},title={Text(text("Delete this comment?","このコメントを削除しますか？"))},text={Text(target.body.take(300))},confirmButton={TextButton(onClick={deleting=null;scope.launch{
        sending=true;try{model.community.deleteComment(post.id,target.id);notice="Deletion confirmed by the server.";load(true)}catch(e:CancellationException){throw e}catch(e:Exception){commentError=e.message}finally{sending=false}
    }}){Text(text("Delete","削除"))}},dismissButton={TextButton(onClick={deleting=null}){Text(text("Cancel","キャンセル"))}})}
}

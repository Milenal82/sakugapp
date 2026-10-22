// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import coil.compose.SubcomposeAsyncImage
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFB9C3FF),background=Color(0xFF101114),surface=Color(0xFF191B22))){Sakugapp()}}}
}

@Composable
private fun Sakugapp(model:FeedViewModel=viewModel(),app:AppViewModel=viewModel()) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val metered by remember(context){meteredNetwork(context.applicationContext)}.collectAsStateWithLifecycle(initialValue=true)
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState()
    val selected by model.selected.collectAsStateWithLifecycle();val detailError by model.detailError.collectAsStateWithLifecycle()
    val feed by model.state.collectAsStateWithLifecycle();val history by app.history.collectAsStateWithLifecycle()
    val grid=rememberLazyGridState()
    var menu by remember{mutableStateOf(false)};var about by remember{mutableStateOf(false)}
    LaunchedEffect(feed.query){grid.scrollToItem(0)}
    fun search(value:String){app.search(value);model.search(value);nav.popBackStack("posts",false)}
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        app.syncBrowserSession()
        val data=result.data
        if(data?.hasExtra("postId")==true){val id=data.getLongExtra("postId",0);if(id>0){model.openId(id);nav.navigate("post/$id")}}
        else data?.getStringExtra("query")?.let{search(it)}
    }
    val openSite:(String)->Unit={url->browser.launch(Intent(context,BrowserActivity::class.java).putExtra("url",url))}
    CompositionLocalProvider(LocalLanguage provides settings.language){
        if(about)AboutDialog{about=false}
        Scaffold(topBar={Column(Modifier.statusBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                if(entry?.destination?.route!="posts")TextButton(onClick={nav.popBackStack()}){Text("‹")}
                Text("Sakugapp",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Box{TextButton(onClick={menu=true}){Text(text("Menu","メニュー"))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text("Posts")},onClick={menu=false;nav.popBackStack("posts",false)})
                    DropdownMenuItem(text={Text(text("My Account","アカウント"))},onClick={menu=false;nav.navigate("account")})
                    DropdownMenuItem(text={Text("Blog")},onClick={menu=false;openSite("https://blog.sakugabooru.com/")})
                    DropdownMenuItem(text={Text("Forum")},onClick={menu=false;openSite("https://www.sakugabooru.com/forum")})
                    DropdownMenuItem(text={Text("Language")},onClick={menu=false;nav.navigate("language")})
                    DropdownMenuItem(text={Text(text("Settings","設定"))},onClick={menu=false;nav.navigate("settings")})
                    DropdownMenuItem(text={Text(text("About and licenses","アプリ・ライセンス"))},onClick={menu=false;about=true})
                }}
            }
        }}){insets->NavHost(navController=nav,startDestination="posts",modifier=Modifier.padding(insets)){
            composable("posts"){PostsScreen(feed,grid,settings,history,metered,app,::search,{post->model.open(post);nav.navigate("post/${post.id}")},model::loadMore)}
            composable("account"){AccountScreen(app,openSite)}
            composable("language"){LanguageScreen(settings.language,app::language)}
            composable("settings"){SettingsScreen(settings,app)}
            composable("post/{id}"){backStack->
                val id=backStack.arguments?.getString("id")?.toLongOrNull()
                LaunchedEffect(id){if(id!=null && selected?.id!=id)model.openId(id)}
                val post=selected?.takeIf{it.id==id}
                if(post!=null)PostDetail(post,settings,metered,app,::search,{nav.navigate("account")},{score->model.updateScore(post.id,score)})
                else Column(Modifier.padding(16.dp)){if(detailError==null)CircularProgressIndicator()else{Text(detailError.orEmpty(),color=MaterialTheme.colorScheme.error);TextButton(onClick={id?.let(model::openId)}){Text(text("Retry","再試行"))}}}
            }
        }}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostsScreen(state:FeedState,grid:LazyGridState,settings:AppSettings,history:List<String>,metered:Boolean,app:AppViewModel,search:(String)->Unit,open:(Post)->Unit,loadMore:()->Unit) {
    var query by rememberSaveable(state.query){mutableStateOf(state.query)}
    var active by rememberSaveable{mutableStateOf<Long?>(null)}
    var suggestions by remember{mutableStateOf<List<String>>(emptyList())}
    var advanced by remember{mutableStateOf(false)}
    var historyOpen by remember{mutableStateOf(false)}
    var policy by remember{mutableStateOf(false)}
    var average by remember(state.query){mutableStateOf<String?>(null)}
    var averaging by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val allowedPreview=!metered || settings.mobilePreview
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}}
    LaunchedEffect(visible,settings.autoPreview,allowedPreview,grid.isScrollInProgress){
        if(!allowedPreview || active !in visible)active=null
        if(settings.autoPreview && allowedPreview && !grid.isScrollInProgress && active==null)active=state.posts.firstOrNull{it.id in visible && it.isVideo}?.id
    }
    LaunchedEffect(query){
        suggestions=emptyList();val prefix=query.substringAfterLast(' ').removePrefix("-")
        if(prefix.length>=2){delay(450);try{suggestions=app.community.suggestions(prefix)}catch(e:CancellationException){throw e}catch(_:Exception){}}
    }
    fun submit(value:String){focus.clearFocus();suggestions=emptyList();active=null;search(value)}
    Column(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(text("Tags · separate with spaces","タグ・スペースで区切る"))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query)}),trailingIcon={Row{TextButton(onClick={query=""}){Text("×")};TextButton(onClick={submit(query)}){Text(text("Search","検索"))}}})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
            TextButton(onClick={advanced=true}){Text(text("Filters","絞り込み"))}
            TextButton(onClick={historyOpen=true}){Text(text("History","履歴"))}
            TextButton(enabled=!averaging && state.posts.isNotEmpty() && !(settings.wifiImages && metered),onClick={scope.launch{
                averaging=true
                try { val urls=state.posts.filter{it.id in visible}.mapNotNull{it.previewUrl};val result=averagePreviewColor(context,urls);average="${result.first} (${result.second}/${urls.size})" }
                catch(e:CancellationException){throw e}catch(e:Exception){average=e.message}finally{averaging=false}
            }}){Text(text("Average color","平均色"))}
            (if(query!=state.query && query.isNotBlank())suggestions.take(4) else history.take(4)).forEach{value->
                TextButton(onClick={val selected=if(query!=state.query && query.isNotBlank())query.substringBeforeLast(' ',"").let{if(it.isBlank())""else"$it "}+(if(query.substringAfterLast(' ').startsWith('-'))"-"else"")+value else value;query=selected;submit(selected)}){Text(value,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
        }
        average?.let{Text(it,style=MaterialTheme.typography.labelSmall)}
        LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,modifier=Modifier.weight(1f).testTag("post-grid"),contentPadding=PaddingValues(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            state.categoryWarning?.let{item(span={GridItemSpan(2)}){Text(it,style=MaterialTheme.typography.bodySmall)}}
            items(state.posts,key={it.id}){post->PostCard(post,settings,metered,active==post.id,{open(post)},search,{if(!allowedPreview)policy=true else active=if(active==post.id)null else post.id})}
            if(state.loading)items(6,key={"skeleton-$it"}){SkeletonCard()}
            item(span={GridItemSpan(2)}){Column{
                state.error?.let{Text(it,color=MaterialTheme.colorScheme.error);Button(onClick=loadMore){Text(text("Retry this page","このページを再試行"))}}
                if(!state.loading && state.error==null){if(state.posts.isEmpty())Text(text("No posts found.","該当する投稿はありません。"))else if(state.endReached)Text(text("End of results","すべて表示しました"))else Button(onClick=loadMore,modifier=Modifier.fillMaxWidth()){Text(text("Load more","さらに表示"))}}
            }}
        }
    }
    if(historyOpen)AlertDialog(onDismissRequest={historyOpen=false},title={Text(text("Search history","検索履歴"))},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){if(history.isEmpty())Text(text("No saved searches.","検索履歴はありません。"));history.forEach{item->TextButton(onClick={query=item;submit(item);historyOpen=false}){Text(item)}}}},confirmButton={TextButton(onClick={historyOpen=false}){Text(text("Close","閉じる"))}},dismissButton={TextButton(onClick=app::clearHistory){Text(text("Clear","削除"))}})
    if(advanced)AdvancedDialog(query,{value->query=value;submit(value);advanced=false},{advanced=false})
    if(policy)AlertDialog(onDismissRequest={policy=false},title={Text(text("Network policy","ネットワーク設定"))},text={Text(text("Previews on metered networks are disabled in Settings.","従量制回線でのプレビューは設定で無効になっています。"))},confirmButton={TextButton(onClick={policy=false}){Text("OK")}})
}

@Composable private fun SkeletonCard(){
    val transition=rememberInfiniteTransition(label="loading")
    val alpha by transition.animateFloat(0.25f,0.65f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="shimmer")
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=alpha)))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=alpha)))
        Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=alpha)))
    }
}

@Composable private fun PostCard(post:Post,settings:AppSettings,metered:Boolean,preview:Boolean,open:()->Unit,search:(String)->Unit,toggle:()->Unit){
    Column{
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black).clickable(onClick=open)){
            if(preview && post.fileUrl!=null)VideoPanel(post.fileUrl,settings,preview=true)
            else PostImage(post,Modifier.fillMaxSize(),settings,metered)
            if(post.isVideo)TextButton(onClick=toggle,modifier=Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=0.65f))){Text(if(preview)"■"else"▶",color=Color.White)}
        }
        PostCaption(post,settings,search)
    }
}

@Composable private fun PostCaption(post:Post,settings:AppSettings,search:(String)->Unit){
    val tags=post.tags.filter{(it.category==TagCategory.COPYRIGHT && settings.showCopyright)||(it.category==TagCategory.ARTIST && settings.showArtist)}
    val caption=buildAnnotatedString{
        for(tag in tags){if(length>0)append(" ");pushStringAnnotation("tag",tag.name);withStyle(SpanStyle(color=if(tag.category==TagCategory.COPYRIGHT)Color(0xFFFFD54F)else Color(0xFFCE93D8))){append(tag.name)};pop()}
        if(settings.showScore){if(length>0)append(" ");append("Score ${post.score?.toString() ?: "?"}")}
    }
    @Suppress("DEPRECATION")
    ClickableText(caption,modifier=Modifier.fillMaxWidth().padding(top=5.dp),style=MaterialTheme.typography.labelMedium.copy(color=MaterialTheme.colorScheme.onSurfaceVariant),maxLines=if(settings.wrapTags)Int.MAX_VALUE else 1,overflow=TextOverflow.Ellipsis,onClick={offset->caption.getStringAnnotations("tag",offset,offset).firstOrNull()?.let{search(it.item)}})
}

@Composable private fun PostImage(post:Post,modifier:Modifier,settings:AppSettings,metered:Boolean,detail:Boolean=false){
    if(settings.wifiImages && metered){Box(modifier,contentAlignment=Alignment.Center){Text(text("Images: Wi-Fi only","画像：非従量制回線のみ"),Modifier.padding(8.dp))};return}
    val url=if(detail)post.fileUrl ?: post.previewUrl else post.previewUrl
    if(url==null)Box(modifier,contentAlignment=Alignment.Center){Text(text("Image unavailable","画像がありません"))}
    else SubcomposeAsyncImage(model=url,contentDescription="Post ${post.id}",modifier=modifier,contentScale=if(!detail && settings.cropThumbnails)ContentScale.Crop else ContentScale.Fit,loading={Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))},error={Text(text("Image failed to load","画像の読み込みに失敗"),Modifier.padding(8.dp))})
}

@Composable private fun PostDetail(post:Post,settings:AppSettings,metered:Boolean,app:AppViewModel,search:(String)->Unit,login:()->Unit,onScore:(Int?)->Unit){
    val uri=LocalUriHandler.current;var linkError by remember{mutableStateOf(false)}
    var seek by remember{mutableStateOf<Pair<Long,Int>?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("#${post.id}",style=MaterialTheme.typography.headlineMedium)
        if(post.isVideo)VideoPanel(post.fileUrl!!,settings,seekRequest=seek)
        else PostImage(post,Modifier.fillMaxWidth().height(320.dp),settings,metered,true)
        Text("Score: ${post.score?.toString() ?: "?"}")
        if(post.width!=null && post.height!=null)Text("${post.width} × ${post.height}")
        post.fileExt?.let{Text(it)};post.uploader?.let{Text(text("Uploader: ","投稿者：")+it)}
        post.rating?.let{Text("Rating: $it")};post.status?.let{Text("Status: $it")}
        post.createdAt?.let{timestamp->runCatching{DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()))}.getOrNull()?.let{Text(it)}}
        post.source?.let{Text("Source: $it")}
        OutlinedButton(onClick={linkError=runCatching{uri.openUri(post.pageUrl)}.isFailure}){Text(text("Open original post","元の投稿を開く"))}
        if(linkError)Text(text("No browser available.","ブラウザーがありません。"))
        for(tag in post.tags)TextButton(onClick={search(tag.name)}){Text(tag.name,color=when(tag.category){TagCategory.COPYRIGHT->Color(0xFFFFD54F);TagCategory.ARTIST->Color(0xFFCE93D8);else->MaterialTheme.colorScheme.onSurfaceVariant})}
        CommunityPanel(post,app,onScore,login){time->seek=time to ((seek?.second ?: 0)+1)}
    }
}

@Composable private fun AdvancedDialog(query:String,apply:(String)->Unit,close:()->Unit){
    var order by remember{mutableStateOf("date")};var rating by remember{mutableStateOf("all")}
    AlertDialog(onDismissRequest=close,title={Text(text("Search filters","検索条件"))},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){
        Text(text("Order","並び順"));for(value in listOf("date","score","score_asc","id","id_desc","random"))Row{RadioButton(selected=order==value,onClick={order=value});Text(value,Modifier.padding(top=12.dp))}
        Text("Rating");for(value in listOf("all","safe","questionable","explicit"))Row{RadioButton(selected=rating==value,onClick={rating=value});Text(value,Modifier.padding(top=12.dp))}
    }},confirmButton={TextButton(onClick={apply(PreferenceRules.advanced(query,order,rating))}){Text(text("Apply","適用"))}},dismissButton={TextButton(onClick=close){Text(text("Cancel","キャンセル"))}})
}

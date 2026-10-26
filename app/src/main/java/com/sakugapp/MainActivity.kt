// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import coil.compose.SubcomposeAsyncImage
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity:AppCompatActivity(),VideoOrientationHost{
    override var hasFullscreenPlayer=false
    private fun browseOrientation(){requestedOrientation=if(resources.configuration.smallestScreenWidthDp>=600)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);if(!hasFullscreenPlayer)browseOrientation()}

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);browseOrientation();enableEdgeToEdge();setContent{Sakugapp()}
    }
    override fun onResume(){
        super.onResume()
        lifecycleScope.launch{LocaleCoordinator.reconcile(this@MainActivity)}
    }
}

@Composable
private fun Sakugapp(model:FeedViewModel=viewModel(),app:AppViewModel=viewModel()) {
    val savedSettings by app.settings.collectAsStateWithLifecycle()
    val settings=savedSettings.copy(lightTheme=themeIsLight(savedSettings.themeMode,isSystemInDarkTheme()),columns=GridPolicy.effective(savedSettings.columns,LocalConfiguration.current.smallestScreenWidthDp))
    val context=LocalContext.current
    val metered by remember(context){meteredNetwork(context.applicationContext)}.collectAsStateWithLifecycle(initialValue=true)
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState()
    val selected by model.selected.collectAsStateWithLifecycle();val detailError by model.detailError.collectAsStateWithLifecycle()
    val rawFeed by model.state.collectAsStateWithLifecycle()
    val library by app.library.collectAsStateWithLifecycle();val dictionaries by app.dictionaries.collectAsStateWithLifecycle()
    val feed=remember(rawFeed,library.durations){rawFeed.copy(posts=rawFeed.posts.map{p->library.durations[p.fileUrl]?.let{p.copy(durationMs=it)} ?: p})}
    val history by app.history.collectAsStateWithLifecycle()
    val grid=rememberLazyGridState()
    val systemTag=android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()
    val language=PreferenceRules.resolveLanguage(settings.language,systemTag)
    var menu by remember{mutableStateOf(false)};var about by remember{mutableStateOf(false)}
    var scrollQuery by rememberSaveable{mutableStateOf(feed.query)}
    LaunchedEffect(feed.query){if(scrollQuery!=feed.query){grid.scrollToItem(0);scrollQuery=feed.query}}
    fun search(value:String){app.search(value);model.search(value);nav.popBackStack("posts",false)}
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==android.app.Activity.RESULT_OK)app.syncBrowserSession()
        val data=result.data
        if(data?.hasExtra("postId")==true){val id=data.getLongExtra("postId",0);if(id>0){model.openId(id);nav.navigate("post/$id")}}
        else data?.getStringExtra("query")?.let{search(it)}
    }
    val openSite:(String)->Unit={url->browser.launch(Intent(context,BrowserActivity::class.java).putExtra("url",url))}
    SakugappTheme(settings.lightTheme){CompositionLocalProvider(LocalLanguage provides language,LocalTagPresentation provides TagPresentation(settings.tagTranslations,dictionaries[if(language=="zh-TW")"zh-TW"else"zh-CN"].orEmpty(),library.translations,app.localLibrary)){
        if(about)AboutDialog{about=false}
        Scaffold(topBar={Column(Modifier.statusBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                if(entry?.destination?.route!="posts")TextButton(onClick={nav.popBackStack()},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_back_to_app)}){Text("‹")}
                Text(text(R.string.app_name),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Box{TextButton(onClick={menu=true}){Text(text(R.string.ui_menu))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(text(R.string.ui_posts))},onClick={menu=false;nav.popBackStack("posts",false)})
                    DropdownMenuItem(text={Text(text(R.string.ui_local_library))},onClick={menu=false;nav.navigate("library")})
                    DropdownMenuItem(text={Text(text(R.string.ui_my_account))},onClick={menu=false;nav.navigate("account")})
                    DropdownMenuItem(text={Text(text(R.string.ui_blog))},onClick={menu=false;openSite("https://blog.sakugabooru.com/")})
                    DropdownMenuItem(text={Text(text(R.string.ui_forum))},onClick={menu=false;openSite("https://www.sakugabooru.com/forum")})
                    DropdownMenuItem(text={Text(text(R.string.ui_language))},onClick={menu=false;nav.navigate("language")})
                    DropdownMenuItem(text={Text(text(R.string.ui_settings))},onClick={menu=false;nav.navigate("settings")})
                    DropdownMenuItem(text={Text(text(R.string.ui_about_and_licenses))},onClick={menu=false;about=true})
                }}
            }
        }}){insets->NavHost(navController=nav,startDestination="posts",modifier=Modifier.padding(insets)){
            composable("posts"){PostsScreen(feed,grid,settings,history,metered,app,::search,{post->model.open(post);nav.navigate("post/${post.id}")},model::loadMore)}
            composable("library"){LibraryScreen(app){post->model.open(post);nav.navigate("post/${post.id}")}}
            composable("account"){AccountScreen(app,openSite)}
            composable("language"){LanguageScreen(settings.language,app::language)}
            composable("settings"){SettingsScreen(settings,app)}
            composable("post/{id}"){backStack->
                val id=backStack.arguments?.getString("id")?.toLongOrNull()
                LaunchedEffect(id){if(id!=null && selected?.id!=id)model.openId(id)}
                val post=selected?.takeIf{it.id==id}
                if(post!=null)PostDetail(post,settings,metered,app,::search,openSite,{nav.navigate("account")},{score->model.updateScore(post.id,score)},{duration->model.updateDuration(post.id,post.fileUrl.orEmpty(),duration);app.duration(post.fileUrl.orEmpty(),duration)})
                else Column(Modifier.padding(16.dp)){if(detailError==null)CircularProgressIndicator()else{Text(translateMessage(detailError.orEmpty()),color=MaterialTheme.colorScheme.error);TextButton(onClick={id?.let(model::openId)}){Text(text(R.string.ui_retry))}}}
            }
        }}
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostsScreen(state:FeedState,grid:LazyGridState,settings:AppSettings,history:List<String>,metered:Boolean,app:AppViewModel,search:(String)->Unit,open:(Post)->Unit,loadMore:()->Unit) {
    var query by rememberSaveable(state.query,stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(state.query,TextRange(state.query.length)))}
    var active by rememberSaveable{mutableStateOf<Long?>(null)}
    var advanced by remember{mutableStateOf(false)}
    var historyOpen by rememberSaveable{mutableStateOf(false)}
    var policy by remember{mutableStateOf(false)}
    var gridMenu by remember{mutableStateOf(false)}
    var searchFocused by remember{mutableStateOf(false)}
    var chromeVisible by rememberSaveable{mutableStateOf(true)}
    var baseHeight by remember{mutableIntStateOf(0)}
    var fullHeight by remember{mutableIntStateOf(0)}
    var average by remember(state.query){mutableStateOf<String?>(null)}
    var averaging by remember{mutableStateOf(false)}
    val context=LocalContext.current;val density=LocalDensity.current
    val scope=rememberCoroutineScope();val focus=LocalFocusManager.current
    val allowedPreview=!metered || settings.mobilePreview
    val suggestions=rememberSuggestions(query,searchFocused,app.suggestions)
    val pinned=searchFocused || suggestions.active || historyOpen || advanced
    val pinnedNow by rememberUpdatedState(pinned)
    val chrome=remember(density){ScrollChrome(with(density){64.dp.toPx()},with(density){28.dp.toPx()})}
    val connection=remember(chrome){object:NestedScrollConnection{
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(!pinnedNow)chromeVisible=chrome.consume(consumed.y,chromeVisible)
            return Offset.Zero
        }
    }}
    val offset by animateFloatAsState(if(chromeVisible || pinned)0f else -fullHeight.toFloat(),tween(Motion.search,easing=FastOutSlowInEasing),label="search-chrome")
    LaunchedEffect(pinned){if(pinned){chromeVisible=true;chrome.reset()}}
    LaunchedEffect(grid,state.query){snapshotFlow{!grid.canScrollBackward}.collect{if(it){chromeVisible=true;chrome.reset()}}}
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(grid,settings.columns){
        snapshotFlow {
            val current=latestState
            val last=grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            current.posts.isNotEmpty() && last>=current.posts.size-12 && !current.loading && current.error==null && !current.endReached && !current.autoLoadPaused
        }.distinctUntilChanged().filter{it}.collect{loadMore()}
    }
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}}
    LaunchedEffect(visible,settings.autoPreview,allowedPreview,grid.isScrollInProgress){
        if(!allowedPreview || active !in visible)active=null
        if(settings.autoPreview && allowedPreview && !grid.isScrollInProgress && active==null)active=state.posts.firstOrNull{it.id in visible && it.isVideo}?.id
    }
    fun submit(value:String){focus.clearFocus();active=null;historyOpen=false;chromeVisible=true;search(value)}
    fun complete(name:String){
        if(!query.selection.collapsed || query.composition!=null)return
        val edit=SearchEditing.complete(query.text,query.selection.start,name);query=TextFieldValue(edit.text,TextRange(edit.cursor))
    }
    Box(Modifier.fillMaxSize().clipToBounds()){
        LazyVerticalGrid(columns=GridCells.Fixed(settings.columns),state=grid,
            modifier=Modifier.fillMaxSize().nestedScroll(connection).testTag("post-grid"),
            contentPadding=PaddingValues(start=10.dp,end=10.dp,top=with(density){baseHeight.toDp()},bottom=16.dp),
            horizontalArrangement=Arrangement.spacedBy(if(settings.columns>=8)settings.cardSpacing.coerceAtMost(2f).dp else settings.cardSpacing.dp),verticalArrangement=Arrangement.spacedBy(if(settings.columns>=8)2.dp else (settings.cardSpacing+2f).dp)){
            state.categoryWarning?.let{item(span={GridItemSpan(maxLineSpan)}){Column{
                Text(text(R.string.ui_tag_categories_unavailable_unknown_categories_are_not_guessed),style=MaterialTheme.typography.bodySmall)
                Text(translateMessage(it),style=MaterialTheme.typography.labelSmall)
                TextButton(enabled=!state.loading,onClick={submit(state.query)}){Text(text(R.string.ui_reload_posts_and_tags))}
            }}}
            items(state.posts,key={it.id}){post->PostCard(post,settings,metered,active==post.id,{open(post)},search,{if(!allowedPreview)policy=true else active=if(active==post.id)null else post.id})}
            if(state.loading && state.posts.isEmpty())items(settings.columns.coerceAtLeast(6),key={"skeleton-$it"}){SkeletonCard()}
            item(span={GridItemSpan(maxLineSpan)}){Column(Modifier.fillMaxWidth().padding(vertical=8.dp)){
                if(state.loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text(text(R.string.ui_loading_more_posts),style=MaterialTheme.typography.bodySmall)}
                state.error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);Button(onClick=loadMore){Text(text(R.string.ui_retry_this_page))}}
                if(!state.loading && state.error==null){if(state.posts.isEmpty())Text(text(R.string.ui_no_posts_found))else if(state.endReached)Text(text(R.string.ui_end_of_results))else Button(onClick=loadMore,modifier=Modifier.fillMaxWidth()){Text(text(R.string.ui_load_more))}}
                if(grid.canScrollBackward)TextButton(onClick={scope.launch{grid.animateScrollToItem(0)}}){Text(text(R.string.ui_back_to_top))}
            }}
        }
        Surface(Modifier.fillMaxWidth().offset{IntOffset(0,offset.toInt())}.zIndex(1f).onSizeChanged{fullHeight=it.height},shadowElevation=2.dp){
            Column(Modifier.padding(horizontal=10.dp).heightIn(max=with(LocalConfiguration.current){screenHeightDp.dp*0.7f})){
                Column(Modifier.onSizeChanged{baseHeight=it.height},verticalArrangement=Arrangement.spacedBy(4.dp)){
                    OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().onFocusChanged{searchFocused=it.isFocused}.testTag("search-input"),singleLine=true,
                        label={Text(text(R.string.ui_tags_separate_with_spaces))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                        keyboardActions=KeyboardActions(onSearch={if(query.composition==null)submit(query.text)}),
                        trailingIcon={Row{TextButton(onClick={query=TextFieldValue("")},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_clear_search)}){Text("×")};TextButton(onClick={if(query.composition==null)submit(query.text)}){Text(text(R.string.ui_search))}}})
                    SearchSyntax.warnings(query.text).forEach{Text(translateMessage(it),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        TextButton(onClick={advanced=true}){Text(text(R.string.ui_filters))}
                        TextButton(onClick={historyOpen=!historyOpen;focus.clearFocus()}){Text(text(R.string.ui_history))}
                        TextButton(enabled=!averaging && state.posts.isNotEmpty() && !(settings.wifiImages && metered),onClick={scope.launch{
                            averaging=true
                            try{val urls=state.posts.filter{it.id in visible}.mapNotNull{it.previewUrl};val result=averagePreviewColor(context,urls);average="${result.first} (${result.second}/${urls.size})"}
                            catch(e:CancellationException){throw e}catch(e:Exception){average=localizedMessage(context,e.message.orEmpty())}finally{averaging=false}
                        }}){Text(text(R.string.ui_average_color))}
                        Box{
                            TextButton(onClick={gridMenu=true},modifier=Modifier.testTag("grid-columns").semantics{contentDescription=context.getString(R.string.ui_grid_selection,settings.columns)}){Text("▦ ${settings.columns}")}
                            DropdownMenu(expanded=gridMenu,onDismissRequest={gridMenu=false}){for(columns in GridPolicy.options(LocalConfiguration.current.smallestScreenWidthDp))DropdownMenuItem(text={Text(text(R.string.ui_columns_count,columns))},onClick={
                                val index=grid.firstVisibleItemIndex;val scroll=grid.firstVisibleItemScrollOffset
                                app.columns(columns);grid.requestScrollToItem(index,scroll);gridMenu=false
                            })}
                        }
                    }
                    average?.let{Text(it,style=MaterialTheme.typography.labelSmall)}
                    HorizontalDivider(thickness=1.dp,color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.testTag("search-divider"))
                }
                AnimatedVisibility(historyOpen,enter=expandVertically(tween(Motion.history)),exit=shrinkVertically(tween(Motion.history))){Column(Modifier.heightIn(max=200.dp).verticalScroll(rememberScrollState()).testTag("search-history")){
                    if(history.isEmpty())Text(text(R.string.ui_no_saved_searches),Modifier.padding(8.dp))
                    history.forEach{item->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={query=TextFieldValue(item,TextRange(item.length));submit(item)},modifier=Modifier.weight(1f)){Text(item)}
                        TextButton(onClick={app.deleteHistory(item)},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_delete_search,item)}){Text("×")}
                    }}
                    TextButton(onClick=app::clearHistory){Text(text(R.string.ui_clear_search_history))}
                }}
                if(!historyOpen)TagSuggestionsMenu(suggestions,settings.lightTheme,::complete)
            }
        }
    }
    if(advanced)SearchDialog(query.text,app.suggestions,settings.lightTheme,{value->query=TextFieldValue(value,TextRange(value.length));submit(value);advanced=false},{advanced=false})
    if(policy)AlertDialog(onDismissRequest={policy=false},title={Text(text(R.string.ui_network_policy))},text={Text(text(R.string.ui_previews_on_metered_networks_are_disabled_in_settings))},confirmButton={TextButton(onClick={policy=false}){Text(text(R.string.ui_ok))}})
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

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PostCard(post:Post,settings:AppSettings,metered:Boolean,preview:Boolean,open:()->Unit,search:(String)->Unit,toggle:()->Unit){
    val contextDescriptionDurationUnknown=text(R.string.ui_duration_unknown)
    var options by remember(post.id){mutableStateOf(false)}
    Surface(shape=RoundedCornerShape(if(settings.columns>=8)6.dp else 12.dp),color=MaterialTheme.colorScheme.surface,
        shadowElevation=if(settings.columns>=8)0.dp else 1.dp,tonalElevation=1.dp){Column{
        Box(Modifier.fillMaxWidth().heightIn(min=48.dp).aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("post-media-${post.id}").combinedClickable(onClick=open,onLongClick={options=true},onLongClickLabel=text(R.string.ui_preview_options))){
            if(preview && post.fileUrl!=null)VideoPanel(post.fileUrl,settings,preview=true)
            else PostImage(post,Modifier.fillMaxSize(),settings,metered)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom){
                if(post.isVideo)Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=0.92f),shape=RoundedCornerShape(3.dp)){
                    Text(post.durationMs?.let(::mediaTime) ?: "—",fontSize=if(settings.columns>=8)9.sp else 11.sp,
                        modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp).semantics{if(post.durationMs==null)contentDescription=contextDescriptionDurationUnknown})
                }
                if(settings.showScore)Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=0.92f),shape=RoundedCornerShape(3.dp)){
                    Text(if(settings.columns>=8)post.score?.toString() ?: "?" else text(R.string.ui_score_value,post.score?.toString() ?: "?"),
                        color=scoreColor(settings.lightTheme),fontSize=if(settings.columns>=8)9.sp else 11.sp,fontWeight=FontWeight.Medium,
                        modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp).testTag("post-score-${post.id}"))
                }
            }
            DropdownMenu(options,{options=false}){
                DropdownMenuItem(text={Text(text(R.string.ui_open_original_post))},onClick={options=false;open()})
                if(post.isVideo)DropdownMenuItem(text={Text(text(if(preview)R.string.ui_stop_preview else R.string.ui_play_preview))},onClick={options=false;toggle()})
            }
        }
        Box(Modifier.padding(horizontal=if(settings.columns>=8)2.dp else 6.dp,vertical=3.dp)){PostCaption(post,settings,search)}
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PostCaption(post:Post,settings:AppSettings,search:(String)->Unit){
    val size=when(settings.columns){1->14.sp;2->12.sp;else->11.sp}
    val tags=captionTags(post,settings)
    if(!settings.wrapTags){
        Column(Modifier.fillMaxWidth().padding(top=5.dp).testTag("post-caption-${post.id}").semantics(mergeDescendants=true){}){
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                tags.forEach{tag->TranslatedTag(tag,search=search,modifier=Modifier.weight(1f),dense=settings.columns>=8,
                    color=when(tag.category){TagCategory.COPYRIGHT->copyrightColor(settings.lightTheme);TagCategory.ARTIST->artistColor(settings.lightTheme);else->MaterialTheme.colorScheme.onSurfaceVariant},fontSize=size,maxLines=1)}
            }
        }
        return
    }
    FlowRow(Modifier.fillMaxWidth().padding(top=5.dp).testTag("post-caption-${post.id}").semantics(mergeDescendants=true){},horizontalArrangement=Arrangement.spacedBy(4.dp)){
        for(tag in tags)TranslatedTag(tag,search=search,dense=settings.columns>=8,color=when(tag.category){TagCategory.COPYRIGHT->copyrightColor(settings.lightTheme);TagCategory.ARTIST->artistColor(settings.lightTheme);else->MaterialTheme.colorScheme.onSurfaceVariant},fontSize=size,
            maxLines=if(settings.wrapTags && settings.columns<=2)Int.MAX_VALUE else 2)
    }
}

@Composable private fun PostImage(post:Post,modifier:Modifier,settings:AppSettings,metered:Boolean,detail:Boolean=false){
    if(settings.wifiImages && metered){Box(modifier,contentAlignment=Alignment.Center){Text(text(R.string.ui_images_wi_fi_only),Modifier.padding(8.dp))};return}
    val url=if(detail)post.fileUrl ?: post.previewUrl else post.previewUrl
    if(url==null)Box(modifier,contentAlignment=Alignment.Center){Text(text(R.string.ui_image_unavailable))}
    else SubcomposeAsyncImage(model=url,contentDescription=text(R.string.ui_post_description,post.id),modifier=modifier,contentScale=if(!detail && settings.cropThumbnails)ContentScale.Crop else ContentScale.Fit,loading={Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))},error={Text(text(R.string.ui_image_failed_to_load),Modifier.padding(8.dp))})
}

@Composable private fun PostDetail(post:Post,settings:AppSettings,metered:Boolean,app:AppViewModel,search:(String)->Unit,wiki:(String)->Unit,login:()->Unit,onScore:(Int?)->Unit,onDuration:(Long)->Unit){
    val uri=LocalUriHandler.current;var linkError by remember{mutableStateOf(false)}
    var seek by remember{mutableStateOf<Pair<Long,Int>?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(post.isVideo)VideoPanel(app.offlineMedia.existing(post)?.toURI()?.toString() ?: post.fileUrl!!,settings,seekRequest=seek,onDurationChanged=onDuration)
        else PostImage(post,Modifier.fillMaxWidth().height(320.dp),settings,metered,true)
        FavoriteControl(post,app)
        if(post.isVideo)VideoDownloadButton(post)
        Text(text(R.string.ui_score_detail,post.score?.toString() ?: "?"))
        PostMetadata(post){url->linkError=runCatching{uri.openUri(url)}.isFailure}
        OutlinedButton(onClick={linkError=runCatching{uri.openUri(post.pageUrl)}.isFailure}){Text(text(R.string.ui_open_original_post))}
        if(linkError)Text(text(R.string.ui_no_browser_available))
        DetailTags(post.tags,settings.lightTheme,search,wiki)
        CommunityPanel(post,app,onScore,login){time->seek=time to ((seek?.second ?: 0)+1)}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PostMetadata(post:Post,open:(String)->Unit){
    val context=LocalContext.current
    val fields=buildList{
        add(text(R.string.ui_post_id) to "#${post.id}")
        post.uploader?.let{add(text(R.string.ui_uploader) to it)}
        post.createdAt?.let{time->runCatching{DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochSecond(time).atZone(ZoneId.systemDefault()))}.getOrNull()?.let{add(text(R.string.ui_uploaded) to it)}}
        if(post.width!=null && post.height!=null)add(text(R.string.ui_resolution) to "${post.width} × ${post.height}")
        post.fileExt?.let{add(text(R.string.ui_format) to it)}
        post.fileSize?.let{add(text(R.string.ui_file_size) to android.text.format.Formatter.formatFileSize(context,it))}
        post.rating?.let{add(text(R.string.ui_rating) to it)}
        post.status?.let{add(text(R.string.ui_status) to it)}
    }
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        fields.forEach{(label,value)->Column(Modifier.widthIn(max=280.dp)){
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer{Text(value,style=MaterialTheme.typography.bodyMedium)}
        }}
    }
    post.source?.let{source->Column{
        Text(text(R.string.ui_source),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer{Text(source,style=MaterialTheme.typography.bodyMedium)}
        PostParser.safeUrl(source)?.let{url->TextButton(onClick={open(url)}){Text(text(R.string.ui_open_source))}}
    }}
}

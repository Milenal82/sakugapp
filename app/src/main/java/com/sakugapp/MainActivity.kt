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
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sakugapp(model:FeedViewModel=viewModel(),app:AppViewModel=viewModel()) {
    val savedSettings by app.settings.collectAsStateWithLifecycle()
    val settings=savedSettings.copy(lightTheme=themeIsLight(savedSettings.themeMode,isSystemInDarkTheme()),columns=GridPolicy.effective(savedSettings.columns,LocalConfiguration.current.smallestScreenWidthDp))
    val context=LocalContext.current
    val metered by remember(context){meteredNetwork(context.applicationContext)}.collectAsStateWithLifecycle(initialValue=true)
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState()
    val selected by model.selected.collectAsStateWithLifecycle();val detailError by model.detailError.collectAsStateWithLifecycle()
    val rawFeed by model.state.collectAsStateWithLifecycle()
    val library by app.library.collectAsStateWithLifecycle()
    val feed=remember(rawFeed,library.durations){rawFeed.copy(posts=rawFeed.posts.map{p->library.durations[p.fileUrl]?.let{p.copy(durationMs=it)} ?: p})}
    val history by app.history.collectAsStateWithLifecycle()
    val grid=rememberLazyGridState()
    val systemTag=android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()
    val language=PreferenceRules.resolveLanguage(settings.language,systemTag)
    var menu by remember{mutableStateOf(false)};var about by remember{mutableStateOf(false)}
    LaunchedEffect(entry?.destination?.route){if(entry?.destination?.route=="posts")model.restoreBrowse()}
    fun search(value:String){
        app.search(value);model.beginSearch(value)
        if(nav.currentDestination?.route!="search" && !nav.popBackStack("search",false)){
            nav.popBackStack("posts",false);nav.navigate("search")
        }
    }
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==android.app.Activity.RESULT_OK)app.syncBrowserSession()
        val data=result.data
        if(data?.hasExtra("postId")==true){val id=data.getLongExtra("postId",0);if(id>0){model.openId(id);nav.navigate("post/$id")}}
        else data?.getStringExtra("query")?.let{search(it)}
    }
    val openSite:(String)->Unit={url->browser.launch(Intent(context,BrowserActivity::class.java).putExtra("url",url))}
    SakugappTheme(settings.lightTheme){CompositionLocalProvider(LocalLanguage provides language,LocalTagActions provides app){
        if(about)AboutDialog{about=false}
        Scaffold(topBar={Column(Modifier.statusBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                if(entry?.destination?.route !in setOf("posts","library"))TextButton(onClick={nav.popBackStack()},modifier=Modifier.semantics{contentDescription=context.getString(R.string.ui_back_to_app)}){Text("‹")}
                Text(text(R.string.app_name),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Box{TextButton(onClick={menu=true}){Text(text(R.string.ui_menu))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(text(R.string.ui_posts))},onClick={menu=false;nav.popBackStack("posts",false)})
                    DropdownMenuItem(text={Text(text(R.string.ui_local_library))},onClick={menu=false;nav.navigate("library")})
                    DropdownMenuItem(text={Text(text(R.string.ui_browsing_history))},onClick={menu=false;nav.navigate("history")})
                    DropdownMenuItem(text={Text(text(R.string.ui_my_account))},onClick={menu=false;nav.navigate("account")})
                    DropdownMenuItem(text={Text(text(R.string.ui_blog))},onClick={menu=false;openSite("https://blog.sakugabooru.com/")})
                    DropdownMenuItem(text={Text(text(R.string.ui_forum))},onClick={menu=false;openSite("https://www.sakugabooru.com/forum")})
                    DropdownMenuItem(text={Text(text(R.string.ui_language))},onClick={menu=false;nav.navigate("language")})
                    DropdownMenuItem(text={Text(text(R.string.ui_settings))},onClick={menu=false;nav.navigate("settings")})
                    DropdownMenuItem(text={Text(text(R.string.ui_about_and_licenses))},onClick={menu=false;about=true})
                }}
            }
        }}){insets->NavHost(navController=nav,startDestination="posts",modifier=Modifier.padding(insets).semantics{testTagsAsResourceId=true}){
            composable("posts"){
                val browse=model.browsingState
                val home=browse.copy(posts=browse.posts.map{post->library.durations[post.fileUrl]?.let{post.copy(durationMs=it)} ?: post})
                PostsScreen(home,grid,settings,history,metered,app,::search,{post->model.open(post);nav.navigate("post/${post.id}")},model::loadMore,model::refresh,isActive=entry?.destination?.route=="posts")
            }
            composable("search"){PostsScreen(feed,rememberLazyGridState(),settings,history,metered,app,::search,{post->model.open(post);nav.navigate("post/${post.id}")},model::loadMore,model::refresh,isActive=entry?.destination?.route=="search")}
            composable("library"){LibraryScreen(app,::search,{nav.navigate("tags")}){post,order->model.openLocal(post,order);nav.navigate("post/${post.id}")}}
            composable("tags"){TagFavoritesScreen(app,{nav.popBackStack()}){value->nav.popBackStack("posts",false);search(value)}}
            composable("history"){BrowsingHistoryScreen(app,{nav.popBackStack()}){post,order->model.openLocal(post,order);nav.navigate("post/${post.id}")}}
            composable("account"){AccountScreen(app,openSite)}
            composable("language"){LanguageScreen(settings.language,app::language)}
            composable("settings"){SettingsScreen(settings,app)}
            composable("post/{id}"){backStack->
                val id=backStack.arguments?.getString("id")?.toLongOrNull()
                LaunchedEffect(id){if(id!=null && selected?.id!=id)model.openId(id)}
                val post=selected?.takeIf{it.id==id}
                if(post!=null)key(post.id){
                    LaunchedEffect(post.id){app.visited(post);model.prefetchNeighbors()}
                    val neighboringUrls=listOfNotNull(model.adjacent(-1)?.previewUrl,model.adjacent(1)?.previewUrl).distinct()
                    DisposableEffect(post.id,neighboringUrls,settings.wifiImages,metered){
                        val requests=if(settings.wifiImages && metered)emptyList()else neighboringUrls.map{url->coil.Coil.imageLoader(context).enqueue(coil.request.ImageRequest.Builder(context.applicationContext).data(url).size(320).build())}
                        onDispose{requests.forEach{it.dispose()}}
                    }
                    PostDetail(post,settings,metered,app,model.usesLocalSnapshot(),::search,openSite,{nav.navigate("account")},{score->model.updateScore(post.id,score)},{duration->model.updateDuration(post.id,post.fileUrl.orEmpty(),duration);app.duration(post.fileUrl.orEmpty(),duration)},model.adjacent(-1)!=null,model.adjacent(1)!=null,{delta->
                        val next=model.adjacent(delta)
                        if(next==null){model.prefetchNeighbors();false}else{
                            model.selectAdjacent(next)
                            nav.navigate("post/${next.id}"){popUpTo(entry!!.destination.id){inclusive=true};launchSingleTop=true}
                            true
                        }
                    })}

                else Column(Modifier.padding(16.dp)){if(detailError==null)CircularProgressIndicator()else{Text(translateMessage(detailError.orEmpty()),color=MaterialTheme.colorScheme.error);TextButton(onClick={id?.let(model::openId)}){Text(text(R.string.ui_retry))}}}
            }
        }}
    }}
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable
internal fun PostsScreen(state:FeedState,grid:LazyGridState,settings:AppSettings,history:List<String>,metered:Boolean,app:AppViewModel,search:(String)->Unit,open:(Post)->Unit,loadMore:()->Unit,refresh:()->Unit,isActive:Boolean=true) {
    var query by rememberSaveable(state.query,stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(state.query,TextRange(state.query.length)))}
    var active by rememberSaveable{mutableStateOf<Long?>(null)}
    var advanced by remember{mutableStateOf(false)}
    var historyOpen by rememberSaveable{mutableStateOf(false)}
    var policy by remember{mutableStateOf(false)}
    var gridMenu by remember{mutableStateOf(false)}
    var searchFocused by remember{mutableStateOf(false)}
    var searchExpanded by rememberSaveable{mutableStateOf(false)}
    val searchFocus=remember{FocusRequester()}
    var chromeVisible by rememberSaveable{mutableStateOf(true)}
    var baseHeight by remember{mutableIntStateOf(0)}
    var fullHeight by remember{mutableIntStateOf(0)}
    var average by remember(state.query){mutableStateOf<String?>(null)}
    var averaging by remember{mutableStateOf(false)}
    val context=LocalContext.current;val density=LocalDensity.current
    val scope=rememberCoroutineScope();val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val imeVisible=WindowInsets.isImeVisible
    var scrollQuery by rememberSaveable{mutableStateOf(state.query)}
    LaunchedEffect(state.query){if(scrollQuery!=state.query){grid.scrollToItem(0);scrollQuery=state.query}}
    val allowedPreview=!metered || settings.mobilePreview
    val suggestions=rememberSuggestions(query,searchFocused,app.suggestions)
    val pinned=searchExpanded || advanced
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
    LaunchedEffect(grid,settings.columns,isActive){
        snapshotFlow {
            val current=latestState
            val last=grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            isActive && current.posts.isNotEmpty() && last>=current.posts.size-12 && !current.loading && !current.refreshing && current.refreshError==null && current.error==null && !current.endReached && !current.autoLoadPaused
        }.distinctUntilChanged().filter{it}.collect{loadMore()}
    }
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}}
    LaunchedEffect(visible,settings.autoPreview,allowedPreview,grid.isScrollInProgress){
        if(!allowedPreview || active !in visible)active=null
        if(settings.autoPreview && allowedPreview && !grid.isScrollInProgress && active==null)active=state.posts.firstOrNull{it.id in visible && it.isVideo}?.id
    }
    fun collapseSearch(){searchExpanded=false;historyOpen=false;keyboard?.hide();focus.clearFocus()}
    fun submit(value:String){collapseSearch();active=null;chromeVisible=true;search(value)}
    // Let the IME own system back while visible; the editor consumes the next back event.
    BackHandler(enabled=isActive && searchExpanded && !advanced && !imeVisible){collapseSearch()}
    LaunchedEffect(searchExpanded){if(searchExpanded){searchFocus.requestFocus()}else focus.clearFocus()}

    fun complete(name:String){
        if(!query.selection.collapsed || query.composition!=null)return
        val edit=SearchEditing.complete(query.text,query.selection.start,name);query=TextFieldValue(edit.text,TextRange(edit.cursor))
    }
    val refreshState=rememberPullToRefreshState()
    var shownRefresh by rememberSaveable(state.query){mutableIntStateOf(state.refreshRevision)}
    LaunchedEffect(state.refreshRevision){if(shownRefresh!=state.refreshRevision){shownRefresh=state.refreshRevision;grid.requestScrollToItem(0)}}
    PullToRefreshBox(isRefreshing=state.refreshing,onRefresh={if(!grid.canScrollBackward && !searchExpanded)refresh()},state=refreshState,
        modifier=Modifier.fillMaxSize().clipToBounds().testTag("posts-refresh"),
        indicator={PullToRefreshDefaults.Indicator(state=refreshState,isRefreshing=state.refreshing,
            modifier=Modifier.align(Alignment.TopCenter).padding(top=64.dp).testTag("refresh-indicator").semantics{contentDescription=context.getString(R.string.ui_refresh_posts)})}){
        LazyVerticalGrid(columns=GridCells.Fixed(settings.columns),state=grid,
            modifier=Modifier.fillMaxSize().nestedScroll(connection).testTag("post-grid"),
            contentPadding=PaddingValues(start=if(settings.columns>=6)2.dp else 10.dp,end=if(settings.columns>=6)2.dp else 10.dp,top=with(density){baseHeight.toDp()},bottom=if(state.refreshError!=null)120.dp else 16.dp),
            horizontalArrangement=Arrangement.spacedBy(if(settings.columns>=6)settings.cardSpacing.coerceAtMost(1f).dp else settings.cardSpacing.dp),verticalArrangement=Arrangement.spacedBy(if(settings.columns>=6)1.dp else (settings.cardSpacing+2f).dp)){
            state.categoryWarning?.let{item(span={GridItemSpan(maxLineSpan)}){Column{
                Text(text(R.string.ui_tag_categories_unavailable_unknown_categories_are_not_guessed),style=MaterialTheme.typography.bodySmall)
                Text(translateMessage(it),style=MaterialTheme.typography.labelSmall)
                TextButton(enabled=!state.loading,onClick={submit(state.query)}){Text(text(R.string.ui_reload_posts_and_tags))}
            }}}
            items(state.posts,key={it.id}){post->PostCard(post,settings,metered,active==post.id,{collapseSearch();open(post)},::submit,{if(!allowedPreview)policy=true else active=if(active==post.id)null else post.id},{actual->app.duration(post.fileUrl.orEmpty(),actual)})}
            if(state.loading && state.posts.isEmpty())items(settings.columns.coerceAtLeast(6),key={"skeleton-$it"}){SkeletonCard()}
            item(span={GridItemSpan(maxLineSpan)}){Column(Modifier.fillMaxWidth().padding(vertical=8.dp)){
                if(state.loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text(text(R.string.ui_loading_more_posts),style=MaterialTheme.typography.bodySmall)}
                state.error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);Button(enabled=!state.refreshing,onClick=loadMore){Text(text(R.string.ui_retry_this_page))}}
                if(!state.loading && state.error==null){if(state.posts.isEmpty())Text(text(R.string.ui_no_posts_found))else if(state.endReached)Text(text(R.string.ui_end_of_results))else Button(enabled=!state.refreshing,onClick=loadMore,modifier=Modifier.fillMaxWidth()){Text(text(R.string.ui_load_more))}}
                if(grid.canScrollBackward)TextButton(onClick={scope.launch{grid.animateScrollToItem(0)}}){Text(text(R.string.ui_back_to_top))}
            }}
        }
        state.refreshError?.let{failure->Snackbar(modifier=Modifier.align(Alignment.BottomCenter).padding(12.dp).zIndex(2f),action={
            TextButton(onClick=refresh,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.inversePrimary),modifier=Modifier.testTag("retry-refresh")){Text(text(R.string.ui_retry))}
        }){Column{Text(text(R.string.ui_refresh_failed));Text(translateMessage(failure),style=MaterialTheme.typography.bodySmall)}}}
        Surface(Modifier.fillMaxWidth().offset{IntOffset(0,offset.toInt())}.zIndex(1f).onSizeChanged{fullHeight=it.height},tonalElevation=0.dp){
            Column(Modifier.padding(horizontal=10.dp).heightIn(max=with(LocalConfiguration.current){screenHeightDp.dp*0.7f})){
                Row(Modifier.fillMaxWidth().height(64.dp).onSizeChanged{baseHeight=it.height},verticalAlignment=Alignment.CenterVertically){
                    if(searchExpanded){
                        OutlinedTextField(query,{query=it},modifier=Modifier.weight(1f).focusRequester(searchFocus).onFocusChanged{searchFocused=it.isFocused}.testTag("search-input"),singleLine=true,
                            label={Text(text(R.string.ui_search))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                            keyboardActions=KeyboardActions(onSearch={submit(query.text)}),
                            trailingIcon={ActionIcon(R.drawable.ic_close,text(R.string.ui_clear_search),{query=TextFieldValue("")},Modifier.testTag("clear-search"))})
                        // Explicit search accepts the visible draft; only suggestions defer IME composition.
                        ActionIcon(R.drawable.ic_search,text(R.string.ui_search),{submit(query.text)},Modifier.testTag("submit-search"))
                        ActionIcon(R.drawable.ic_close,text(R.string.ui_close),::collapseSearch,Modifier.testTag("collapse-search"))
                    }else{
                        Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(role=Role.Button){chromeVisible=true;historyOpen=false;searchExpanded=true}
                            .testTag("search-entry").semantics(mergeDescendants=true){contentDescription=context.getString(R.string.ui_search)}
                            .padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_search),contentDescription=null,modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(state.query.ifBlank{text(R.string.ui_search)},maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(state.query.isNotBlank())SearchTagFavorite(state.query,state.posts,app)else Box{
                            TextButton(onClick={gridMenu=true},modifier=Modifier.testTag("grid-columns").semantics{contentDescription=context.getString(R.string.ui_grid_selection,settings.columns)}){Text("▦ ${settings.columns}")}
                            DropdownMenu(expanded=gridMenu,onDismissRequest={gridMenu=false}){for(columns in GridPolicy.options(LocalConfiguration.current.smallestScreenWidthDp))DropdownMenuItem(text={Text(text(R.string.ui_columns_count,columns))},onClick={
                                val index=grid.firstVisibleItemIndex;val scroll=grid.firstVisibleItemScrollOffset
                                app.columns(columns);grid.requestScrollToItem(index,scroll);gridMenu=false
                            })}
                        }
                    }
                }
                AnimatedVisibility(searchExpanded,enter=expandVertically(tween(Motion.search))+fadeIn(tween(Motion.search)),exit=shrinkVertically(tween(Motion.search))+fadeOut(tween(Motion.search))){
                    Column(Modifier.verticalScroll(rememberScrollState()).testTag("search-expanded")){
                        SearchSyntax.warnings(query.text).forEach{Text(translateMessage(it),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            TextButton(onClick={collapseSearch();advanced=true},modifier=Modifier.testTag("open-combined-search")){Text(text(R.string.ui_filters))}
                            TextButton(onClick={historyOpen=!historyOpen;focus.clearFocus()}){Text(text(R.string.ui_history))}
                            TextButton(enabled=!averaging && state.posts.isNotEmpty() && !(settings.wifiImages && metered),onClick={scope.launch{
                                averaging=true
                                try{val urls=state.posts.filter{it.id in visible}.mapNotNull{it.previewUrl};val result=averagePreviewColor(context,urls);average="${result.first} (${result.second}/${urls.size})"}
                                catch(e:CancellationException){throw e}catch(e:Exception){average=localizedMessage(context,e.message.orEmpty())}finally{averaging=false}
                            }}){Text(text(R.string.ui_average_color))}
                        }
                        average?.let{Text(it,style=MaterialTheme.typography.labelSmall)}
                        AnimatedVisibility(historyOpen,enter=expandVertically(tween(Motion.history)),exit=shrinkVertically(tween(Motion.history))){Column(Modifier.testTag("search-history")){
                            if(history.isEmpty())Text(text(R.string.ui_no_saved_searches),Modifier.padding(8.dp))
                            history.forEach{item->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                TextButton(onClick={query=TextFieldValue(item,TextRange(item.length));submit(item)},modifier=Modifier.weight(1f)){Text(item,maxLines=2,overflow=TextOverflow.Ellipsis)}
                                ActionIcon(R.drawable.ic_close,context.getString(R.string.ui_delete_search,item),{app.deleteHistory(item)})
                            }}
                            if(history.isNotEmpty())TextButton(onClick=app::clearHistory){Text(text(R.string.ui_clear_search_history))}
                        }}
                        if(!historyOpen)TagSuggestionsMenu(suggestions,settings.lightTheme,::complete)
                    }
                }
                HorizontalDivider(thickness=1.dp,color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.testTag("search-divider"))
            }
        }
    }
    if(advanced)SearchDialog(query.text,app.suggestions,settings.lightTheme,{value->query=TextFieldValue(value,TextRange(value.length));submit(value);advanced=false},{advanced=false},history)
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
@Composable internal fun PostCard(post:Post,settings:AppSettings,metered:Boolean,preview:Boolean,open:()->Unit,search:(String)->Unit,toggle:()->Unit,onDuration:(Long)->Unit){
    val contextDescriptionDurationUnknown=text(R.string.ui_duration_unknown)
    var options by remember(post.id){mutableStateOf(false)}
    val interaction=remember(post.id){MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if(pressed && settings.columns<=4)0.985f else 1f,tween(Motion.press,easing=FastOutSlowInEasing),label="card-press")
    Surface(modifier=if(settings.columns<=4)Modifier.graphicsLayer{scaleX=pressScale;scaleY=pressScale}else Modifier,shape=RoundedCornerShape(if(settings.columns>=6)6.dp else 12.dp),color=MaterialTheme.colorScheme.surface,
        shadowElevation=if(settings.columns>=6)0.dp else 1.dp,tonalElevation=1.dp){Column{
        Box(Modifier.fillMaxWidth().heightIn(min=if(settings.columns>=6)24.dp else 48.dp).aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("post-media-${post.id}").combinedClickable(interactionSource=interaction,indication=LocalIndication.current,onClick=open,onLongClick={options=true},onLongClickLabel=text(R.string.ui_preview_options))){
            if(preview && post.fileUrl!=null)VideoPanel(post.fileUrl,settings,preview=true,onDurationChanged=onDuration)
            else PostImage(post,Modifier.fillMaxSize(),settings,metered)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom){
                if(post.isVideo && settings.columns<6)Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=0.96f),shape=RoundedCornerShape(3.dp)){
                    Text(post.durationMs?.let(::mediaTime) ?: "—",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=if(settings.columns>=6)9.sp else 11.sp,
                        lineHeight=if(settings.columns>=6)11.sp else 13.sp,
                        modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp).semantics{if(post.durationMs==null)contentDescription=contextDescriptionDurationUnknown})
                }
                Spacer(Modifier.weight(1f))
                if(settings.showScore)Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=0.96f),shape=RoundedCornerShape(3.dp)){
                    Text(if(settings.columns>=4)post.score?.toString() ?: "?" else text(R.string.ui_score_value,post.score?.toString() ?: "?"),
                        maxLines=1,overflow=TextOverflow.Ellipsis,
                        color=scoreColor(settings.lightTheme),fontSize=if(settings.columns>=6)9.sp else 11.sp,lineHeight=if(settings.columns>=6)11.sp else 13.sp,fontWeight=FontWeight.Medium,
                        modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp).testTag("post-score-${post.id}"))
                }
            }
            DropdownMenu(options,{options=false}){
                DropdownMenuItem(text={Text(text(R.string.ui_open_original_post))},onClick={options=false;open()})
                if(post.isVideo)DropdownMenuItem(text={Text(text(if(preview)R.string.ui_stop_preview else R.string.ui_play_preview))},onClick={options=false;toggle()})
            }
        }
        Box(Modifier.padding(horizontal=if(settings.columns>=6)2.dp else 6.dp,vertical=3.dp)){PostCaption(post,settings,search)}
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PostCaption(post:Post,settings:AppSettings,search:(String)->Unit){
    val size=when(settings.columns){1->14.sp;2->12.sp;else->11.sp}
    val tags=captionTags(post,settings).let{if(settings.columns>=6)it.take(2)else it}
    if(!settings.wrapTags || settings.columns>=6){
        Column(Modifier.fillMaxWidth().padding(top=5.dp).testTag("post-caption-${post.id}").semantics(mergeDescendants=true){}){
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                tags.forEach{tag->OriginalTag(tag,search=search,modifier=Modifier.weight(1f),
                    color=when(tag.category){TagCategory.COPYRIGHT->copyrightColor(settings.lightTheme);TagCategory.ARTIST->artistColor(settings.lightTheme);else->MaterialTheme.colorScheme.onSurfaceVariant},fontSize=size,maxLines=1)}
            }
        }
        return
    }
    FlowRow(Modifier.fillMaxWidth().padding(top=5.dp).testTag("post-caption-${post.id}").semantics(mergeDescendants=true){},horizontalArrangement=Arrangement.spacedBy(4.dp)){
        for(tag in tags)OriginalTag(tag,search=search,color=when(tag.category){TagCategory.COPYRIGHT->copyrightColor(settings.lightTheme);TagCategory.ARTIST->artistColor(settings.lightTheme);else->MaterialTheme.colorScheme.onSurfaceVariant},fontSize=size,
            maxLines=if(settings.wrapTags && settings.columns<=2)Int.MAX_VALUE else 2)
    }
}

@Composable private fun PostImage(post:Post,modifier:Modifier,settings:AppSettings,metered:Boolean,detail:Boolean=false){
    val compact=!detail && settings.columns>=6
    @Composable fun state(id:Int){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        if(compact)Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_close),contentDescription=text(id),modifier=Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        else Text(text(id),Modifier.padding(8.dp))
    }}
    if(settings.wifiImages && metered){Box(modifier){state(R.string.ui_images_wi_fi_only)};return}
    val url=if(detail)post.fileUrl ?: post.previewUrl else post.previewUrl
    if(url==null)Box(modifier){state(R.string.ui_image_unavailable)}
    else SubcomposeAsyncImage(model=url,contentDescription=text(R.string.ui_post_description,post.id),modifier=modifier,contentScale=if(!detail && settings.cropThumbnails)ContentScale.Crop else ContentScale.Fit,loading={Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))},error={state(R.string.ui_image_failed_to_load)})
}

@Composable internal fun PostDetail(post:Post,settings:AppSettings,metered:Boolean,app:AppViewModel,localSnapshot:Boolean,search:(String)->Unit,wiki:(String)->Unit,login:()->Unit,onScore:(Int?)->Unit,onDuration:(Long)->Unit,hasPrevious:Boolean,hasNext:Boolean,onNavigate:(Int)->Boolean){
    val uri=LocalUriHandler.current;var linkError by remember{mutableStateOf(false)}
    var seek by remember{mutableStateOf<Pair<Long,Int>?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(localSnapshot)Text(text(R.string.ui_library_snapshot_hint),style=MaterialTheme.typography.bodySmall)
        if(post.isVideo)VideoPanel(app.offlineMedia.existing(post)?.toURI()?.toString() ?: post.fileUrl!!,settings,seekRequest=seek,onDurationChanged=onDuration,onNavigate=onNavigate)
        else PostImage(post,Modifier.fillMaxWidth().height(320.dp),settings,metered,true)
        Row(Modifier.widthIn(max=360.dp).fillMaxWidth().testTag("detail-actions"),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
            ActionIcon(R.drawable.ic_previous,text(R.string.ui_previous_post),{onNavigate(-1)},Modifier.testTag("previous-post"),enabled=hasPrevious)
            ActionIcon(R.drawable.ic_next,text(R.string.ui_next_post),{onNavigate(1)},Modifier.testTag("next-post"),enabled=hasNext)
            if(post.isVideo)VideoDownloadButton(post)else ActionIcon(R.drawable.ic_download,text(R.string.ui_download_video),{},enabled=false)
            ActionIcon(R.drawable.ic_open,text(R.string.ui_open_original_post),{linkError=runCatching{uri.openUri(post.pageUrl)}.isFailure},Modifier.testTag("original-post"))
            FavoriteControl(post,app)
        }
        PostMetadata(post,settings.lightTheme){url->linkError=runCatching{uri.openUri(url)}.isFailure}
        if(linkError)Text(text(R.string.ui_no_browser_available))
        DetailTags(post.tags,settings.lightTheme,search,wiki)
        CommunityPanel(post,app,onScore,login){time->seek=time to ((seek?.second ?: 0)+1)}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PostMetadata(post:Post,light:Boolean,open:(String)->Unit){
    val context=LocalContext.current
    val fields=buildList{
        post.uploader?.let{add(text(R.string.ui_uploader) to it)}
        post.createdAt?.let{time->runCatching{DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochSecond(time).atZone(ZoneId.systemDefault()))}.getOrNull()?.let{add(text(R.string.ui_uploaded) to it)}}
        if(post.width!=null && post.height!=null)add(text(R.string.ui_resolution) to "${post.width} × ${post.height}")
        post.fileExt?.let{add(text(R.string.ui_format) to it)}
        post.fileSize?.let{add(text(R.string.ui_file_size) to android.text.format.Formatter.formatFileSize(context,it))}
        post.rating?.let{add(text(R.string.ui_rating) to it)}
        post.status?.let{add(text(R.string.ui_status) to it)}
    }
    FlowRow(Modifier.fillMaxWidth().testTag("post-metadata"),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("${text(R.string.ui_post_id)} #${post.id}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("detail-post-id"))
        post.source?.takeIf{it.isNotBlank()}?.let{source->
            val url=PostParser.safeUrl(source)
            Text("${text(R.string.ui_source)}: $source",style=MaterialTheme.typography.bodySmall,
                color=if(url==null)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines=2,overflow=TextOverflow.Ellipsis,
                modifier=Modifier.widthIn(max=280.dp).testTag("post-source").then(if(url==null)Modifier else Modifier.clickable{open(url)}))
        }
        Text(text(R.string.ui_score_value,post.score?.toString() ?: "?"),color=scoreColor(light),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("detail-score"))
        fields.forEach{(label,value)->SelectionContainer{Text("$label: $value",style=MaterialTheme.typography.bodySmall,modifier=Modifier.widthIn(max=280.dp))}}
    }
}

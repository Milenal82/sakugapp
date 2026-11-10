// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sakugapp.PostCard
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

@Composable internal fun TagLookup(repository:DiscoveryRepository,type:TagCategory,select:(String)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var suggestions by remember{mutableStateOf(emptyList<TagSuggestion>())};var error by remember{mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)};var loading by remember{mutableStateOf(false)}
    LaunchedEffect(query,type,retry){suggestions=emptyList();error=null;loading=false;if(query.trim().length>=2){loading=true;try{delay(350);suggestions=repository.tags(query,type)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}}}
    OutlinedTextField(query,{query=it},singleLine=true,label={Text(text(if(type==TagCategory.ARTIST)R.string.ui_artist else R.string.ui_copyright))},modifier=Modifier.fillMaxWidth().testTag("discovery-query"))
    Text(text(R.string.ui_catalog_limit),style=MaterialTheme.typography.bodySmall)
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("discovery-tag-loading"))
    error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text(text(R.string.ui_retry))}}
    if(query.length>=2 && !loading && error==null && suggestions.isEmpty())Text(text(R.string.ui_no_matching_verified_tags))
    suggestions.forEach{tag->OutlinedButton(onClick={select(tag.name)},modifier=Modifier.fillMaxWidth().testTag("candidate-${tag.name}")){Text(tag.name)}}
    if(query.isNotBlank())TextButton(onClick={select(query.trim())}){Text(text(R.string.ui_verify_tag))}
}
@Composable internal fun SampleFooter(state:DiscoveryState,model:DiscoveryViewModel,tag:String,type:TagCategory){
    state.sample?.let{Text(text(R.string.ui_sample_count,it.posts.size,it.pages),style=MaterialTheme.typography.bodySmall);if(it.capped)Text(text(R.string.ui_sample_cap),style=MaterialTheme.typography.bodySmall);if(it.ended)Text(text(R.string.ui_sample_end),style=MaterialTheme.typography.bodySmall);it.warning?.let{Text(text(R.string.ui_tag_categories_unavailable_unknown_categories_are_not_guessed),style=MaterialTheme.typography.bodySmall)}}
    if(state.loading){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=model::cancel){Text(text(R.string.ui_cancel))}}
    state.error?.let{Text(text(if(it.contains("classification"))R.string.ui_classification_unknown else R.string.ui_discovery_error),color=MaterialTheme.colorScheme.error);if(!it.contains("classification"))Text(translateMessage(it),style=MaterialTheme.typography.bodySmall);TextButton(onClick={model.retry(tag,type)}){Text(text(R.string.ui_retry))}}
    if(!state.loading){Row{OutlinedButton(enabled=state.sample?.let{!it.ended && !it.capped}==true,onClick=model::more){Text(text(R.string.ui_continue_sampling))};TextButton(onClick={model.select(tag,type,true)}){Text(text(R.string.ui_sample_refresh))}}}
}
@Composable internal fun ArtistStatsScreen(repository:DiscoveryRepository,model:DiscoveryViewModel,search:(String)->Unit){
    val state by model.state.collectAsStateWithLifecycle();var tag by rememberSaveable{mutableStateOf("")}
    LaunchedEffect(tag){if(tag.isNotBlank() && state.sample==null && !state.loading)model.select(tag,TagCategory.ARTIST)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.cancel()}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("artist-stats"),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(text(R.string.ui_home_artists),style=MaterialTheme.typography.titleLarge)
        TagLookup(repository,TagCategory.ARTIST){tag=it;model.select(it,TagCategory.ARTIST)}
        if(tag.isNotEmpty()){
            Text(tag,style=MaterialTheme.typography.titleMedium)
            state.statistics?.let{stats->
                Text(text(R.string.ui_average_score,stats.mean?.let{String.format(java.util.Locale.ROOT,"%.1f",it)} ?: text(R.string.ui_no_calculable_data)))
                Text(text(R.string.ui_score_denominator,stats.scoreCount,stats.count),style=MaterialTheme.typography.bodySmall)
                Text(text(R.string.ui_upload_years),style=MaterialTheme.typography.titleMedium)
                stats.years.forEach{(year,count)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(year.toString());LinearProgressIndicator(progress={count.toFloat()/stats.count.coerceAtLeast(1)},modifier=Modifier.weight(1f).align(Alignment.CenterVertically));Text(count.toString())}}
                if(stats.missingYears>0)Text(text(R.string.ui_missing_dates,stats.missingYears))
                Text(text(R.string.ui_common_shows),style=MaterialTheme.typography.titleMedium)
                stats.copyrights.take(30).forEach{Frequency(it){search(SearchSyntax.add(tag,it.tag.name,"and"))}}
                Text(text(R.string.ui_common_tags),style=MaterialTheme.typography.titleMedium);Text(text(R.string.ui_cooccurrence_note),style=MaterialTheme.typography.bodySmall)
                stats.related.take(50).forEach{Frequency(it){search(SearchSyntax.add(tag,it.tag.name,"and"))}}
            }
            SampleFooter(state,model,tag,TagCategory.ARTIST)
            OutlinedButton(onClick={search(tag)}){Text(text(R.string.ui_view_posts))}
        }
    }}
}
@Composable private fun Frequency(value:TagFrequency,click:()->Unit){
    Surface(onClick=click,shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(value.tag.name,Modifier.weight(1f));Text("${value.count} · ${String.format(java.util.Locale.ROOT,"%.1f",value.percent)}%")}
    }
}
@Composable internal fun ShowBrowserScreen(repository:DiscoveryRepository,select:(String)->Unit){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("show-browser"),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(text(R.string.ui_home_shows),style=MaterialTheme.typography.titleLarge);TagLookup(repository,TagCategory.COPYRIGHT,select)
    }}
}
@Composable internal fun episodeLabel(key:EpisodeKey)=when(key.kind){EpisodeKind.EPISODE->text(R.string.ui_episode_number,key.number ?: 0);EpisodeKind.OP->"OP"+(key.number?.toString() ?: "");EpisodeKind.ED->"ED"+(key.number?.toString() ?: "");EpisodeKind.MOVIE->text(R.string.ui_movie);EpisodeKind.OVA->"OVA"+(key.number?.toString() ?: "");EpisodeKind.PV->"PV / Trailer";EpisodeKind.OTHER->text(R.string.ui_uncategorized);EpisodeKind.MISSING->text(R.string.ui_missing_source)}
@Composable internal fun ShowDetailScreen(tag:String,model:DiscoveryViewModel,search:(String)->Unit,episode:(EpisodeKey)->Unit){
    val state by model.state.collectAsStateWithLifecycle();var number by rememberSaveable{mutableStateOf("")};var artists by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(tag){model.select(tag,TagCategory.COPYRIGHT)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.cancel()}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("show-detail"),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(tag,style=MaterialTheme.typography.titleLarge);Text(text(R.string.ui_source_sampling_note),style=MaterialTheme.typography.bodySmall)
        Text(text(R.string.ui_discovered_groups,state.groups.size))
        OutlinedTextField(number,{number=it.take(4)},label={Text(text(R.string.ui_find_episode))},singleLine=true)
        OutlinedButton(enabled=number.toIntOrNull() in 1..1899,onClick={episode(EpisodeKey(EpisodeKind.EPISODE,number.toInt()))}){Text(text(R.string.ui_search))}
        state.groups.forEach{(group,posts)->OutlinedButton(onClick={episode(group)},modifier=Modifier.fillMaxWidth().testTag("episode-${group.key}")){Text("${episodeLabel(group)} · ${posts.size}")}}
        SampleFooter(state,model,tag,TagCategory.COPYRIGHT)
        OutlinedButton(onClick={search(tag)}){Text(text(R.string.ui_view_posts))}
        TextButton(onClick={artists=!artists}){Text(text(R.string.ui_common_artists))}
        if(artists)state.sample?.let{sample->state.statistics?.related.orEmpty().filter{it.tag.category==TagCategory.ARTIST}.take(25).forEach{Frequency(it){search(SearchSyntax.add(tag,it.tag.name,"and"))}}}
    }}
}
@Composable internal fun EpisodePostsScreen(tag:String,key:EpisodeKey,model:DiscoveryViewModel,settings:AppSettings,app:AppViewModel,search:(String)->Unit,open:(Post,List<Post>)->Unit,isActive:Boolean){
    val state by model.state.collectAsStateWithLifecycle();val grid=rememberLazyGridState();var preview by remember{mutableStateOf<Long?>(null)}
    LaunchedEffect(tag){model.select(tag,TagCategory.COPYRIGHT)};LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.cancel();preview=null}
    val scoreChanges by app.scoreChanges.collectAsStateWithLifecycle();val facts by app.library.collectAsStateWithLifecycle()
    val posts=remember(state.groups[key],scoreChanges,facts.durations){state.groups[key].orEmpty().map{p->p.copy(score=scoreChanges[p.id] ?: p.score,durationMs=facts.durations[p.fileUrl] ?: p.durationMs)}}
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}.toSet()}}
    LaunchedEffect(visible,isActive){if(!isActive || preview !in visible)preview=null}
    LazyVerticalGrid(GridCells.Fixed(settings.columns),state=grid,modifier=Modifier.fillMaxSize().testTag("episode-posts"),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(settings.cardSpacing.coerceIn(0f,10f).dp),verticalArrangement=Arrangement.spacedBy(settings.cardSpacing.coerceIn(0f,10f).dp)){
        item(span={GridItemSpan(maxLineSpan)}){Column{
            Text("$tag · ${episodeLabel(key)}",style=MaterialTheme.typography.titleMedium)
            Text(text(R.string.ui_source_sampling_note),style=MaterialTheme.typography.bodySmall)
            if(posts.isEmpty() && !state.loading)Text(text(R.string.ui_episode_not_found))
            SampleFooter(state,model,tag,TagCategory.COPYRIGHT)
        }}
        items(posts,key={it.id}){post->PostCard(post,settings,false,isActive && preview==post.id,{preview=null;open(post,posts)},search,{preview=if(preview==post.id)null else post.id},{app.duration(post.fileUrl.orEmpty(),it)})}
    }
}

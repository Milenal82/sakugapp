// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExploreScreen(search:(String)->Unit,route:(String)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(text(R.string.ui_explore),style=MaterialTheme.typography.titleLarge)
        Text(text(R.string.ui_popular_posts),style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("day" to R.string.ui_pop_day,"week" to R.string.ui_pop_week,"month" to R.string.ui_pop_month,"year" to R.string.ui_pop_year).forEach{(period,label)->SearchToolTile(R.drawable.ic_star,text(label),{search(DateRangeFilter.popular(period))},Modifier.testTag("topic-$period"))}
        }
        Text(text(R.string.ui_discover_tags),style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(Triple("artist","date",R.string.ui_new_artist),Triple("artist","count",R.string.ui_pop_artist),Triple("copyright","date",R.string.ui_new_copyright),Triple("copyright","count",R.string.ui_pop_copyright)).forEach{(type,order,label)->SearchToolTile(R.drawable.ic_book,text(label),{route("topic_tags/$type/$order")})}
        }
        Text(text(R.string.ui_animation_categories),style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("character_acting" to R.string.ui_topic_acting,"fighting" to R.string.ui_topic_fighting,"liquid" to R.string.ui_topic_liquid,"explosions" to R.string.ui_topic_explosions,"hair" to R.string.ui_topic_hair,"production_materials" to R.string.ui_topic_production).forEach{(tag,label)->SearchToolTile(R.drawable.ic_search,text(label),{search(tag)},Modifier.testTag("topic-$tag"))}
        }
    }
}
@Composable internal fun TopicTagsScreen(repository:TopicRepository,type:TagCategory,order:String,search:(String)->Unit){
    var tags by remember{mutableStateOf(emptyList<DirectoryTag>())};var page by rememberSaveable{mutableIntStateOf(1)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var end by remember{mutableStateOf(false)};var reload by remember{mutableIntStateOf(0)}
    LaunchedEffect(type,order,page,reload){busy=true;error=null;try{val pages=if(tags.isEmpty())(1..page).toList()else listOf(page);var next=emptyList<DirectoryTag>();val restored=mutableListOf<DirectoryTag>();for(p in pages){next=repository.page(type,order,p,reload>0 && p==1);restored+=next};tags=(if(page==1 || pages.size>1)restored else tags+restored).distinctBy{it.name};end=next.isEmpty()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(text(R.string.ui_discover_tags),style=MaterialTheme.typography.titleLarge);Text(text(R.string.ui_topic_note),style=MaterialTheme.typography.bodySmall)
        tags.forEach{tag->Surface(onClick={search(tag.name)},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.fillMaxWidth().padding(12.dp)){Text(tag.name,Modifier.weight(1f),color=if(type==TagCategory.ARTIST)artistColor(MaterialTheme.colorScheme.background==appColors(true,LocalAccent.current).background)else copyrightColor(MaterialTheme.colorScheme.background==appColors(true,LocalAccent.current).background));Text(tag.count.toString())}}}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(extensionError(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={reload++}){Text(text(R.string.ui_retry))}}
        if(!busy && tags.isEmpty() && error==null)Text(text(R.string.ui_no_matching_verified_tags))
        if(!busy && error==null && !end && page<5)OutlinedButton(onClick={page++}){Text(text(R.string.ui_load_more))}
        TextButton(onClick={page=1;reload++}){Text(text(R.string.ui_sample_refresh))}
    }
}

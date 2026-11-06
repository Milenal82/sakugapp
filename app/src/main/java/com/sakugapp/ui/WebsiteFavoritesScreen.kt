// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import com.sakugapp.PostCard
import com.sakugapp.R
import com.sakugapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WebsiteFavoritesScreen(model:WebsiteFavoritesViewModel,app:AppViewModel,settings:AppSettings,login:()->Unit,search:(String)->Unit,open:(Post,List<Post>)->Unit){
    val account by app.account.collectAsStateWithLifecycle();val state by model.state.collectAsStateWithLifecycle();val grade by model.grade.collectAsStateWithLifecycle()
    val grid=rememberLazyGridState();var preview by remember{mutableStateOf<Long?>(null)}
    LaunchedEffect(model){model.active(true)}
    LifecycleEventEffect(Lifecycle.Event.ON_START){model.active(true)};LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.active(false);preview=null}
    LaunchedEffect(account?.id,grade){preview=null}
    val visible by remember(grid){derivedStateOf{grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}.toSet()}}
    LaunchedEffect(visible){if(preview !in visible)preview=null}
    Column(Modifier.fillMaxSize().testTag("website-favorites")){
        Text(text(R.string.ui_website_favorites),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(12.dp))
        if(account==null){Column(Modifier.padding(12.dp)){Text(text(R.string.ui_favorites_login));OutlinedButton(onClick=login){Text(text(R.string.ui_account_login))}}}
        else{
            FlowRow(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){for(rank in 0..3)SearchToolTile(R.drawable.ic_star,if(rank==0)text(R.string.ui_all_votes)else"★".repeat(rank),{model.grade(rank)},Modifier.testTag("favorites-$rank"),selected=grade==rank)}
            LazyVerticalGrid(GridCells.Fixed(settings.columns),state=grid,contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                item(span={GridItemSpan(maxLineSpan)}){Column{
                    Text(text(R.string.ui_favorites_levels),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled=!state.loading && !state.refreshing,onClick={model.load(true)}){Text(text(R.string.ui_sample_refresh))}
                    if(state.loading || state.refreshing)LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let{Text(extensionError(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={model.load()}){Text(text(R.string.ui_retry))}}
                    if(!state.loading && state.posts.isEmpty() && state.error==null)Text(text(R.string.ui_no_posts_found))
                }}
                items(state.posts,key={it.id}){post->PostCard(post,settings,false,preview==post.id,{preview=null;open(post,state.posts)},search,{preview=if(preview==post.id)null else post.id},{app.duration(post.fileUrl.orEmpty(),it)})}
                item(span={GridItemSpan(maxLineSpan)}){if(!state.endReached && !state.loading && state.error==null)TextButton(onClick={model.load()}){Text(text(R.string.ui_load_more))}}
            }
        }
    }
}

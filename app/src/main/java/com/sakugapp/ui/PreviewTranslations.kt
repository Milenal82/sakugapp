// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sakugapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Only settled, visible Posts request copyright names; no HTTP in item composition or download. */
@Composable internal fun previewTranslations(posts:List<Post>,grid:LazyGridState,enabled:Boolean,active:Boolean,
    source:DetailTranslationSource):Map<Tag,ChineseTagName> {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestPosts by rememberUpdatedState(posts)
    if(!enabled)return emptyMap()
    val names by source.cachedNames.collectAsStateWithLifecycle()
    LaunchedEffect(grid,source,lifecycle,active){if(active)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
        snapshotFlow{if(grid.isScrollInProgress)null else grid.layoutInfo.visibleItemsInfo.mapNotNull{it.key as? Long}}
            .collectLatest{visible->
                if(visible==null)return@collectLatest
                delay(AUTO_PREVIEW_SETTLE_MS)
                val ids=visible.toHashSet()
                latestPosts.filter{it.id in ids}.flatMap{it.tags}.filter{it.category==TagCategory.COPYRIGHT}
                    .distinctBy{it.name}.forEach{source.lookup(it)}
            }
    }}
    return names
}

/** Keep unchanged per-card name maps identical when another visible post's name arrives. */
internal class PreviewLabels {
    private var previous:Map<String,String> = emptyMap()
    private var previousTags:List<Tag>?=null
    private var previousNames:Map<Tag,ChineseTagName>?=null
    private var previousEnabled=false
    fun present(tags:List<Tag>,enabled:Boolean,names:Map<Tag,ChineseTagName>):Map<String,String>{
        if(tags===previousTags && names===previousNames && enabled==previousEnabled)return previous
        val next=if(!enabled)emptyMap()else tags.filter{it.category==TagCategory.COPYRIGHT}.mapNotNull{tag->
            TranslationLabels.preview(tag,true,names).takeIf{it!=tag.name}?.let{tag.name to it}
        }.toMap()
        if(next!=previous)previous=next
        previousTags=tags;previousNames=names;previousEnabled=enabled
        return previous
    }
}

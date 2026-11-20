// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sakugapp.data.*

/** No feed subscription: only visible details request names; leaving cancels pending lookups. */
@Composable internal fun detailNames(postId:Long,tags:List<Tag>,enabled:Boolean,source:DetailTranslationSource):Map<String,ChineseTagName> {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val state=remember(postId,tags,enabled,source){mutableStateOf<Map<String,ChineseTagName>>(emptyMap())}
    LaunchedEffect(state,lifecycle){
        if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            for(tag in tags.distinctBy{it.name}.filter{it.category==TagCategory.ARTIST || it.category==TagCategory.COPYRIGHT}){
                source.lookup(tag)?.let{state.value=state.value+(tag.name to it)}
            }
        }
    }
    return state.value
}
